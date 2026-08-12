"""训练三轴轴承状态分类模型。

适用数据：每个 CSV 包含 Time Stamp、X-axis、Y-axis、Z-axis 三列；文件名包含
healthy、inner 或 outer，以及可选的缺陷尺寸和负载，例如 0.7inner-100watt.csv。

模型：时域/频域特征 + RandomForestClassifier。
划分原则：以原始 CSV 为 group 划分训练集与测试集，绝不把同一个 CSV 的不同
1000 点切片同时放入训练和测试，避免数据泄漏造成虚高准确率。

运行示例：
  python ml/train_bearing_classifier.py --data-dir "E:/.../感应电机在不同负载条件下的三轴轴承振动数据集"
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import StratifiedGroupKFold
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer


SAMPLE_RATE = 10_000
WINDOW_SIZE = 1_000
CLASS_ORDER = ["HEALTHY", "INNER_RACE", "OUTER_RACE"]
BANDS_HZ = ((0, 500), (500, 1000), (1000, 2000), (2000, 4000), (4000, 5000))


def parse_label(csv_path: Path) -> dict[str, object]:
    """从数据集路径解析故障类别、缺陷尺寸和负载；类别是模型训练标签。"""
    text = str(csv_path).lower()
    if "healthy" in text:
        condition = "HEALTHY"
    elif "inner" in text:
        condition = "INNER_RACE"
    elif "outer" in text:
        condition = "OUTER_RACE"
    else:
        raise ValueError(f"无法从文件名识别健康/内圈/外圈类别：{csv_path}")

    size = re.search(r"(\d+(?:\.\d+)?)mm", text)
    load = re.search(r"(\d+)watt", text)
    return {
        "condition": condition,
        "defect_size_mm": float(size.group(1)) if size else None,
        "load_watt": int(load.group(1)) if load else None,
    }


def axis_features(samples: np.ndarray, prefix: str) -> dict[str, float]:
    """计算一条轴向窗口的可解释时域、频域特征。"""
    x = np.asarray(samples, dtype=float)
    x = x - np.mean(x)
    eps = 1e-12
    std = float(np.std(x))
    abs_mean = float(np.mean(np.abs(x)))
    rms = float(np.sqrt(np.mean(np.square(x))))
    peak = float(np.max(np.abs(x)))
    normalized = x / max(std, eps)

    window = np.hanning(len(x))
    power = np.square(np.abs(np.fft.rfft(x * window)))
    freq = np.fft.rfftfreq(len(x), d=1 / SAMPLE_RATE)
    non_dc = power[1:]
    non_dc_freq = freq[1:]
    total_power = float(np.sum(non_dc))
    probability = non_dc / max(total_power, eps)
    dominant_hz = float(non_dc_freq[int(np.argmax(non_dc))]) if len(non_dc) else 0.0
    centroid_hz = float(np.sum(non_dc_freq * non_dc) / max(total_power, eps))

    result = {
        f"{prefix}_rms": rms,
        f"{prefix}_std": std,
        f"{prefix}_abs_mean": abs_mean,
        f"{prefix}_peak": peak,
        f"{prefix}_peak_to_peak": float(np.ptp(x)),
        f"{prefix}_crest_factor": peak / max(rms, eps),
        f"{prefix}_shape_factor": rms / max(abs_mean, eps),
        f"{prefix}_skewness": float(np.mean(np.power(normalized, 3))),
        f"{prefix}_kurtosis": float(np.mean(np.power(normalized, 4))),
        f"{prefix}_dominant_hz": dominant_hz,
        f"{prefix}_spectral_centroid_hz": centroid_hz,
        f"{prefix}_spectral_entropy": float(-np.sum(probability * np.log(probability + eps))),
    }
    for low, high in BANDS_HZ:
        in_band = (freq >= low) & (freq < high)
        result[f"{prefix}_band_{low}_{high}_ratio"] = float(np.sum(power[in_band]) / max(np.sum(power), eps))
    return result


def window_features(window: np.ndarray) -> dict[str, float]:
    """输入形状为 [1000, 3] 的三轴窗口，输出一个模型特征字典。"""
    result: dict[str, float] = {}
    for index, name in enumerate(("x", "y", "z")):
        result.update(axis_features(window[:, index], name))

    rms = np.sqrt(np.mean(np.square(window - np.mean(window, axis=0)), axis=0))
    total = float(np.linalg.norm(rms))
    result["total_rms"] = total
    for index, name in enumerate(("x", "y", "z")):
        result[f"{name}_rms_ratio"] = float(rms[index] / max(total, 1e-12))
    result["xy_correlation"] = float(np.corrcoef(window[:, 0], window[:, 1])[0, 1])
    result["xz_correlation"] = float(np.corrcoef(window[:, 0], window[:, 2])[0, 1])
    result["yz_correlation"] = float(np.corrcoef(window[:, 1], window[:, 2])[0, 1])
    return result


def read_dataset(data_dir: Path) -> tuple[pd.DataFrame, np.ndarray, np.ndarray, pd.DataFrame]:
    """读取所有 CSV，按不重叠 1000 点分窗，并保留来源文件作为分组信息。"""
    rows: list[dict[str, float]] = []
    labels: list[str] = []
    groups: list[str] = []
    metadata: list[dict[str, object]] = []
    csv_files = sorted(data_dir.rglob("*.csv"))
    if not csv_files:
        raise FileNotFoundError(f"未在目录中找到 CSV：{data_dir}")

    for csv_path in csv_files:
        label = parse_label(csv_path)
        frame = pd.read_csv(csv_path, skipinitialspace=True)
        expected = ["X-axis", "Y-axis", "Z-axis"]
        missing = [column for column in expected if column not in frame.columns]
        if missing:
            raise ValueError(f"{csv_path.name} 缺少列：{', '.join(missing)}")
        signal = frame[expected].apply(pd.to_numeric, errors="coerce").dropna().to_numpy(dtype=float)
        usable = len(signal) // WINDOW_SIZE * WINDOW_SIZE
        if usable == 0:
            print(f"跳过不足 {WINDOW_SIZE} 点的文件：{csv_path.name}")
            continue
        group = str(csv_path.relative_to(data_dir))
        for start in range(0, usable, WINDOW_SIZE):
            rows.append(window_features(signal[start:start + WINDOW_SIZE]))
            labels.append(str(label["condition"]))
            groups.append(group)
            metadata.append({"source_file": group, "start_row": start, **label})
        print(f"已读取 {csv_path.name}: {usable // WINDOW_SIZE} 个窗口，标签={label['condition']}")

    return pd.DataFrame(rows), np.asarray(labels), np.asarray(groups), pd.DataFrame(metadata)


def choose_split(features: pd.DataFrame, labels: np.ndarray, groups: np.ndarray, fold: int):
    """使用分层分组交叉验证选取一个完全按源文件隔离的测试折。"""
    splitter = StratifiedGroupKFold(n_splits=5, shuffle=True, random_state=42)
    splits = list(splitter.split(features, labels, groups))
    if not 0 <= fold < len(splits):
        raise ValueError(f"fold 应在 0 到 {len(splits) - 1} 之间")
    return splits[fold]


def main() -> None:
    parser = argparse.ArgumentParser(description="训练三轴轴承健康/内圈/外圈随机森林分类器")
    parser.add_argument("--data-dir", type=Path, required=True, help="38 个 CSV 所在的数据集根目录")
    parser.add_argument("--output-dir", type=Path, default=Path("ml/artifacts"), help="模型和评估结果输出目录")
    parser.add_argument("--fold", type=int, default=0, help="5 折分组验证中用作测试集的折编号（0-4）")
    args = parser.parse_args()

    features, labels, groups, metadata = read_dataset(args.data_dir)
    train_index, test_index = choose_split(features, labels, groups, args.fold)
    train_groups, test_groups = set(groups[train_index]), set(groups[test_index])
    assert train_groups.isdisjoint(test_groups), "同一 CSV 同时进入训练和测试，存在数据泄漏"

    model = Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("classifier", RandomForestClassifier(
            n_estimators=400, min_samples_leaf=2, class_weight="balanced_subsample",
            n_jobs=-1, random_state=42,
        )),
    ])
    model.fit(features.iloc[train_index], labels[train_index])
    prediction = model.predict(features.iloc[test_index])

    args.output_dir.mkdir(parents=True, exist_ok=True)
    report = classification_report(labels[test_index], prediction, labels=CLASS_ORDER, output_dict=True, zero_division=0)
    matrix = confusion_matrix(labels[test_index], prediction, labels=CLASS_ORDER)
    joblib.dump({"model": model, "feature_names": list(features.columns), "sample_rate": SAMPLE_RATE, "window_size": WINDOW_SIZE, "classes": CLASS_ORDER}, args.output_dir / "bearing_random_forest.joblib")
    (args.output_dir / "evaluation.json").write_text(json.dumps({"classification_report": report, "confusion_matrix": matrix.tolist(), "class_order": CLASS_ORDER, "train_files": sorted(train_groups), "test_files": sorted(test_groups)}, ensure_ascii=False, indent=2), encoding="utf-8")
    metadata.iloc[test_index].assign(actual=labels[test_index], predicted=prediction).to_csv(args.output_dir / "test_predictions.csv", index=False, encoding="utf-8-sig")

    print("\n测试文件与训练文件完全隔离。")
    print(classification_report(labels[test_index], prediction, labels=CLASS_ORDER, zero_division=0))
    print("混淆矩阵（行=真实，列=预测；顺序：健康、内圈、外圈）：")
    print(matrix)
    print(f"\n模型：{args.output_dir / 'bearing_random_forest.joblib'}")
    print(f"评估：{args.output_dir / 'evaluation.json'}")


if __name__ == "__main__":
    main()

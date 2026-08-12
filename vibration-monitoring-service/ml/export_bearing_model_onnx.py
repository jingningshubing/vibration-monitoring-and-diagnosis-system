"""将已训练的 scikit-learn 轴承分类模型导出为 ONNX，并验证推理一致性。"""

from __future__ import annotations

import argparse
from pathlib import Path

import joblib
import numpy as np
import onnxruntime as ort
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

from train_bearing_classifier import choose_split, read_dataset


def main() -> None:
    parser = argparse.ArgumentParser(description="导出轴承随机森林 ONNX 模型并验证")
    parser.add_argument("--model", type=Path, default=Path("ml/artifacts/bearing_random_forest.joblib"))
    parser.add_argument("--output", type=Path, default=Path("ml/artifacts/bearing_random_forest.onnx"))
    parser.add_argument("--data-dir", type=Path, required=True, help="用于转换后验证的数据集根目录")
    parser.add_argument("--fold", type=int, default=0, help="与训练时一致的测试折编号")
    args = parser.parse_args()

    bundle = joblib.load(args.model)
    model = bundle["model"]
    feature_names = bundle["feature_names"]
    initial_types = [("features", FloatTensorType([None, len(feature_names)]))]
    onnx_model = convert_sklearn(
        model,
        initial_types=initial_types,
        # 禁用 ZipMap，让 Java 直接拿到二维 float 概率数组，而不是 ONNX Map。
        options={id(model.named_steps["classifier"]): {"zipmap": False}},
        target_opset=17,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(onnx_model.SerializeToString())

    features, labels, groups, _ = read_dataset(args.data_dir)
    _, test_index = choose_split(features, labels, groups, args.fold)
    test_input = features.iloc[test_index].to_numpy(dtype=np.float32)
    sklearn_labels = model.predict(test_input)
    sklearn_probabilities = model.predict_proba(test_input)

    session = ort.InferenceSession(str(args.output), providers=["CPUExecutionProvider"])
    outputs = session.run(None, {session.get_inputs()[0].name: test_input})
    onnx_labels = outputs[0].astype(str)
    onnx_probabilities = outputs[1]
    label_match = float(np.mean(sklearn_labels == onnx_labels))
    max_probability_error = float(np.max(np.abs(sklearn_probabilities - onnx_probabilities)))
    print(f"ONNX 文件：{args.output}")
    print(f"特征数：{len(feature_names)}；验证窗口数：{len(test_input)}")
    print(f"类别一致率：{label_match:.4%}")
    print(f"最大概率绝对误差：{max_probability_error:.8f}")
    if label_match != 1 or max_probability_error > 1e-5:
        raise RuntimeError("ONNX 与 joblib 推理结果不一致，禁止接入后端")


if __name__ == "__main__":
    main()

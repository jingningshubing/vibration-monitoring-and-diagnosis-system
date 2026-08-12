# 轴承状态训练脚本

脚本使用三轴时域和频域统计特征训练随机森林，识别健康、内圈故障、外圈故障。

```powershell
pip install -r ml\requirements.txt

python ml/train_bearing_classifier.py `
  --data-dir "E:\研二\暑期\hn\感应电机在不同负载条件下的三轴轴承振动数据集" `
  --output-dir ml\artifacts
```

每个 CSV 按 1000 点切成不重叠窗口，但训练/测试按 CSV 文件严格隔离，避免数据泄漏。输出包括：

- `bearing_random_forest.joblib`：模型和特征定义；
- `evaluation.json`：分类报告、混淆矩阵及训练/测试文件清单；
- `test_predictions.csv`：每个测试窗口的实际和预测类别。

## 导出 ONNX

```powershell
python ml/export_bearing_model_onnx.py `
  --data-dir "E:\研二\暑期\hn\感应电机在不同负载条件下的三轴轴承振动数据集"
```

脚本会生成 `ml/artifacts/bearing_random_forest.onnx`，并对同一测试折的每一个窗口比较 `.joblib` 与 ONNX 的类别和概率；不完全一致时会直接失败。

依赖：Python 3.10+、numpy、pandas、scikit-learn、joblib。

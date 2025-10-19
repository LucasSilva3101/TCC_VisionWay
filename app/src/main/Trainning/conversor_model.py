from ultralytics import YOLO

# Load the YOLO11 model
model = YOLO("runs/detect/best2/weights/best.pt")

# Export the model to TFLite format
model.export(format="tflite")  # creates 'yolo11n_float32.tflite'
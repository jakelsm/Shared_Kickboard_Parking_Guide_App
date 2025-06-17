import os
import time
from ultralytics import YOLO
import cv2

# YOLO 모델 로드 (segmentation과 pose)
seg_model = YOLO(r"C:\Users\705-4\Documents\GitHub\merong\DSB-FInal-Project\Server\weight\seg_weight.pt", verbose=False)
pose_model = YOLO(r"C:\Users\705-4\Documents\GitHub\merong\DSB-FInal-Project\Server\weight\pose_weight.pt", verbose=False)
load_to_image=r'C:\Users\705-4\Desktop\t_img\image_6.jpg'
seg_model(load_to_image, verbose=False)
pose_model(load_to_image, verbose=False)
# 이미지가 저장된 폴더 경로
image_folder = r'C:\Users\705-4\Desktop\imge'

# 폴더 내 모든 이미지 파일 목록 가져오기
image_files = [f for f in os.listdir(image_folder) if f.endswith(('.jpg', '.png', '.jpeg'))]

# Segmentation 모델 FPS 측정
if len(image_files) > 0:
    start_time = time.time()
    for image_file in image_files:
        image_path = os.path.join(image_folder, image_file)
        test_image = cv2.imread(image_path)
        if test_image is None:
            continue  # 이미지 로드 실패 시 스킵

        seg_model(test_image, verbose=False)  # 모델 추론 수행

    end_time = time.time()
    total_time = end_time - start_time
    num_images = len(image_files)
    fps_seg = num_images / total_time
    print(f"Segmentation 모델 FPS: {fps_seg:.2f} frames per second")
else:
    print("Segmentation 모델의 FPS를 구할 이미지가 없습니다.")

# Pose 모델 FPS 측정
if len(image_files) > 0:
    start_time = time.time()
    for image_file in image_files:
        image_path = os.path.join(image_folder, image_file)
        test_image = cv2.imread(image_path)
        if test_image is None:
            continue  # 이미지 로드 실패 시 스킵

        pose_model(test_image, verbose=False)  # 모델 추론 수행

    end_time = time.time()
    total_time = end_time - start_time
    num_images = len(image_files)
    fps_pose = num_images / total_time
    print(f"Pose 모델 FPS: {fps_pose:.2f} frames per second")
else:
    print("Pose 모델의 FPS를 구할 이미지가 없습니다.")

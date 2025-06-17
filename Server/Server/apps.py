import os
import math
import cv2
import numpy as np
from ultralytics import YOLO
from django.apps import AppConfig

class MyAppConfig(AppConfig):
    name = 'Server'

    def ready(self):
        if os.environ.get('RUN_MAIN', None) != 'true':
            return
        
        # YOLO 모델 로드
        self.seg_yolo_model = YOLO(r"..\weight\seg_weight.pt", verbose=False)    # YOLOv8 Segmentation 모델
        self.pose_yolo_model = YOLO(r"..\weight\pose_weight.pt", verbose=False)  # YOLO-Pose 모델
        test_img_path=(r'C:\Users\705-4\Documents\GitHub\merong\DSB-FInal-Project\test_img.jpg')
        test_image = cv2.imread(test_img_path) #테스트용 이미지 예측
        self.seg_yolo_model(test_image, verbose=False)
        self.pose_yolo_model(test_image, verbose=False)
        print('Pose 및 Segmentation 모델이 로드되었습니다.')

    # 클래스 이름을 딕셔너리로 정의
    class_names = {
        0: 'bike_stand',     # 킥보드가 서 있는 상태
        1: 'bike_lie',       # 킥보드가 누워 있는 상태
        2: 'crosswalk',      # 횡단보도
        3: 'braille_block',  # 점자 블록
        4: 'bike_road',      # 자전거 도로
        5: 'car_road'        # 자동차 도로
    }
    
    # 금지할 클래스 목록 정의
    prohibited_classes = ['crosswalk', 'braille_block', 'bike_road', 'car_road']
    # 해당 클래스에 킥보드가 겹쳐 있는 것을 금함
    
    # 금지 클래스 이름을 결과 코드로 매핑
    prohibited_class_result_map = {
        'crosswalk': 1,
        'braille_block': 2,
        'bike_road': 3,
        'car_road': 4
    }

    def calculate_distance(self, p1, p2):
        """두 점 p1과 p2 간의 유클리드 거리를 계산합니다."""
        return ((p2[0] - p1[0])**2 + (p2[1] - p1[1])**2) ** 0.5

    def calculate_angle(self, p1, p2):
        """두 점 p1과 p2 간의 각도를 계산합니다."""
        return math.degrees(math.atan2(p2[1] - p1[1], p2[0] - p1[0]))

    def determine_orientation(self, handle, front_wheel, distance_threshold=100, angle_threshold=15):
        """핸들과 앞바퀴 간의 거리 및 각도를 기반으로 킥보드의 상태를 분류합니다."""
        distance = self.calculate_distance(handle, front_wheel)
        angle = abs(90 - abs(self.calculate_angle(handle, front_wheel)))
        if distance > distance_threshold and angle <= angle_threshold:
            return 'standing'
        else:
            return 'lying_down'

    def is_point_inside_rect(self, point, rect):
        """주어진 점이 직사각형 내에 있는지 확인합니다."""
        x, y = point
        x_min, y_min, x_max, y_max = rect
        return x_min <= x <= x_max and y_min <= y <= y_max

    def pred(self, img_path):
        """단일 이미지에 대해 킥보드를 검출하고, 상태를 분석하며, 금지된 영역과의 겹침 여부를 확인합니다."""
        image = cv2.imread(img_path)
        if image is None:
            print(f"이미지를 로드할 수 없습니다: {img_path}")
            return -1

        # 이미지의 높이와 너비 추출
        height, width = image.shape[:2]
        center_x, center_y = width // 2, height // 2

        # 중앙 사각형의 좌표 계산
        central_rect_width = int(0.5 * width)
        central_rect_height = int(0.7 * height)
        central_rect_x_min = center_x - central_rect_width // 2
        central_rect_y_min = center_y - central_rect_height // 2
        central_rect_x_max = center_x + central_rect_width // 2
        central_rect_y_max = center_y + central_rect_height // 2
        central_rect = (central_rect_x_min, central_rect_y_min, central_rect_x_max, central_rect_y_max)

        # 모델 추론
        results_seg = self.seg_yolo_model(image, verbose=False)
        results_pose = self.pose_yolo_model(image, verbose=False)

        # 중앙에 가장 가까운 킥보드 선택
        closest_kickboard = None
        kickboard_mask = None
        min_distance = float('inf')

        for result in results_seg:
            boxes = result.boxes
            masks = result.masks  # 마스크 정보 추출
            if boxes is not None and boxes.data is not None:
                for i, (cls_id, conf, xyxy) in enumerate(zip(boxes.cls, boxes.conf, boxes.xyxy)):
                    class_id = int(cls_id.item())
                    cls_name = self.class_names.get(class_id, 'unknown')
                    if cls_name not in ['bike_stand', 'bike_lie']:
                        continue
                    x1, y1, x2, y2 = map(int, xyxy.tolist())
                    bbox_center_x = (x1 + x2) // 2
                    bbox_center_y = (y1 + y2) // 2
                    if self.is_point_inside_rect((bbox_center_x, bbox_center_y), central_rect):
                        distance = self.calculate_distance((bbox_center_x, bbox_center_y), (center_x, center_y))
                        if distance < min_distance:
                            min_distance = distance
                            closest_kickboard = {
                                'class_id': class_id,
                                'class_name': cls_name,
                                'bbox': (x1, y1, x2, y2),
                                'centroid': (bbox_center_x, bbox_center_y)
                            }
                            if masks is not None and masks.data is not None:
                                kickboard_mask = masks.data[i].cpu().numpy()

        if not closest_kickboard:
            print('중앙에 킥보드가 없습니다.')
            return 6

        result = 200

        # Pose 추정 결과 처리
        if results_pose and hasattr(results_pose[0], 'keypoints') and results_pose[0].keypoints is not None:
            result_pose = results_pose[0]
            boxes_pose = result_pose.boxes
            keypoints_pose = result_pose.keypoints

            matched_kp = None
            max_iou = 0

            for box_pose, kp in zip(boxes_pose.xyxy, keypoints_pose):
                x1_pose, y1_pose, x2_pose, y2_pose = map(int, box_pose.tolist())
                # IoU 계산
                inter_x_min = max(closest_kickboard['bbox'][0], x1_pose)
                inter_y_min = max(closest_kickboard['bbox'][1], y1_pose)
                inter_x_max = min(closest_kickboard['bbox'][2], x2_pose)
                inter_y_max = min(closest_kickboard['bbox'][3], y2_pose)
                inter_area = max(0, inter_x_max - inter_x_min) * max(0, inter_y_max - inter_y_min)
                bbox_area = (closest_kickboard['bbox'][2] - closest_kickboard['bbox'][0]) * \
                            (closest_kickboard['bbox'][3] - closest_kickboard['bbox'][1])
                kp_area = (x2_pose - x1_pose) * (y2_pose - y1_pose)
                union_area = bbox_area + kp_area - inter_area
                iou = inter_area / union_area if union_area > 0 else 0

                if iou > max_iou:
                    max_iou = iou
                    matched_kp = kp

            if matched_kp is None:
                print("킥보드의 키포인트를 찾을 수 없습니다.")
                return 7

            # 키포인트 데이터 접근 및 변환
            keypoints_tensor = matched_kp.data  # 텐서 형태의 키포인트 데이터
            keypoints = keypoints_tensor.cpu().numpy()

            # 첫 번째 차원 제거
            keypoints = keypoints[0]  # 이제 keypoints의 형태는 (num_keypoints, 3)

            # 핸들 및 앞바퀴의 인덱스 설정
            handle_idx = 0  # 핸들
            front_wheel_idx = 1  # 앞바퀴

            # 가시성 확인
            if keypoints[handle_idx, 2] > 0.5 and keypoints[front_wheel_idx, 2] > 0.5:
                handle_x = int(keypoints[handle_idx, 0])
                handle_y = int(keypoints[handle_idx, 1])
                front_wheel_x = int(keypoints[front_wheel_idx, 0])
                front_wheel_y = int(keypoints[front_wheel_idx, 1])

                handle = (handle_x, handle_y)
                front_wheel = (front_wheel_x, front_wheel_y)

                # 상태 분류
                orientation = self.determine_orientation(handle, front_wheel)

                if closest_kickboard['class_name'] == 'bike_lie':
                    print("킥보드가 누워 있습니다.")
                    result = 5
                elif closest_kickboard['class_name'] == 'bike_stand':
                    if orientation != 'standing':
                        print("킥보드가 서 있지 않습니다.")
                        result = 7

                # 금지된 영역과의 겹침 여부 판단
                for result_seg in results_seg:
                    boxes = result_seg.boxes
                    masks = result_seg.masks  # 마스크 정보 추출
                    if boxes is not None and boxes.data is not None and masks is not None and masks.data is not None:
                        for i, (cls_id, xyxy) in enumerate(zip(boxes.cls, boxes.xyxy)):
                            class_id = int(cls_id.item())
                            cls_name = self.class_names.get(class_id, 'unknown')
                            if cls_name not in self.prohibited_classes:
                                continue
                            prohibited_mask = masks.data[i].cpu().numpy()
                            # 겹침 여부 확인 (킥보드 마스크와 금지된 영역 마스크 간의 겹침)
                            overlap = np.logical_and(kickboard_mask, prohibited_mask).any()
                            if overlap:
                                collision_code = self.prohibited_class_result_map.get(cls_name, None)
                                if collision_code:
                                    print(f"킥보드가 금지된 영역과 겹칩니다: {cls_name}")
                                    return collision_code
            else:
                print("핸들이나 앞바퀴의 키포인트가 감지되지 않았습니다.")
                return 7
        else:
            print("Pose 추정 결과가 없습니다.")
            return 7

        return result

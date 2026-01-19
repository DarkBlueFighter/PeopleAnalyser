#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mock Server for PeopleAnalyser Android App Testing
用於在 BlueStacks 或其他 Android 模擬器中測試 Android App 的 Mock Server

使用方式:
    python mock_server.py [port]
    
預設監聽 0.0.0.0:8000，可從模擬器透過 10.0.2.2:8000 或 adb reverse 後透過 localhost:8000 存取
"""

import json
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

# Mock Face API 回應資料
MOCK_FACE_RESPONSE = [
    {
        "faceId": "mock-face-001",
        "faceRectangle": {
            "top": 100,
            "left": 120,
            "width": 150,
            "height": 150
        },
        "faceAttributes": {
            "gender": "male",
            "age": 28.0,
            "smile": 0.8,
            "headPose": {
                "yaw": 5.0,
                "pitch": 0.0,
                "roll": 0.0
            }
        }
    },
    {
        "faceId": "mock-face-002",
        "faceRectangle": {
            "top": 120,
            "left": 400,
            "width": 140,
            "height": 140
        },
        "faceAttributes": {
            "gender": "female",
            "age": 32.0,
            "smile": 0.6,
            "headPose": {
                "yaw": -3.0,
                "pitch": 0.0,
                "roll": 0.0
            }
        }
    }
]


class MockFaceAPIHandler(BaseHTTPRequestHandler):
    """處理 Face API 請求的 Mock Handler"""
    
    def log_message(self, format, *args):
        """自訂日誌格式"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        sys.stdout.write(f"[{timestamp}] {format % args}\n")
        sys.stdout.flush()
    
    def do_GET(self):
        """處理 GET 請求（測試用）"""
        self.send_response(200)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        
        response = {
            "status": "ok",
            "message": "Mock Face API Server is running",
            "timestamp": datetime.now().isoformat(),
            "endpoints": {
                "face_detect": "POST /face/v1.0/detect?returnFaceAttributes=age,gender,headPose,smile"
            }
        }
        
        self.wfile.write(json.dumps(response, ensure_ascii=False, indent=2).encode('utf-8'))
        self.log_message('GET %s - 200 OK', self.path)
    
    def do_POST(self):
        """處理 POST 請求（模擬 Face API）"""
        content_length = int(self.headers.get('Content-Length', 0))
        
        # 讀取請求 body（影像資料）
        if content_length > 0:
            body = self.rfile.read(content_length)
            self.log_message('Received %d bytes of image data', len(body))
        
        # 檢查是否為 Face API 端點
        if '/face/v1.0/detect' in self.path or '/detect' in self.path:
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            # 回傳 Mock 資料
            response_json = json.dumps(MOCK_FACE_RESPONSE, ensure_ascii=False, indent=2)
            self.wfile.write(response_json.encode('utf-8'))
            self.log_message('POST %s - 200 OK (returned %d faces)', self.path, len(MOCK_FACE_RESPONSE))
        else:
            # 其他端點回傳 404
            self.send_response(404)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.end_headers()
            
            error_response = {
                "error": "Not Found",
                "message": f"Unknown endpoint: {self.path}"
            }
            self.wfile.write(json.dumps(error_response, ensure_ascii=False).encode('utf-8'))
            self.log_message('POST %s - 404 Not Found', self.path)
    
    def do_OPTIONS(self):
        """處理 OPTIONS 請求（CORS preflight）"""
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Ocp-Apim-Subscription-Key')
        self.end_headers()


def run_server(port=8000):
    """啟動 Mock Server
    
    注意：此 Mock Server 僅供開發測試使用，監聽 0.0.0.0 代表接受所有網路介面的連線。
    在生產環境中，應該使用正式的 API Server 並設定適當的安全性措施。
    """
    server_address = ('0.0.0.0', port)
    httpd = HTTPServer(server_address, MockFaceAPIHandler)
    
    print("=" * 60)
    print(f"Mock Face API Server 已啟動")
    print(f"監聽位址: 0.0.0.0:{port}")
    print("=" * 60)
    print(f"\n本機測試 URL:")
    print(f"  http://127.0.0.1:{port}/")
    print(f"  http://localhost:{port}/")
    print(f"\nBlueStacks 模擬器存取方式:")
    print(f"  方式 1 (直接): http://10.0.2.2:{port}/")
    print(f"  方式 2 (adb reverse): ")
    print(f"    1. 執行: adb reverse tcp:{port} tcp:{port}")
    print(f"    2. App 中使用: http://localhost:{port}/")
    print(f"\n按 Ctrl+C 停止伺服器")
    print("=" * 60)
    print()
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n\n伺服器已停止")
        httpd.server_close()


if __name__ == '__main__':
    port = 8000
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            print(f"錯誤: 無效的 port 參數 '{sys.argv[1]}'")
            print(f"使用方式: python {sys.argv[0]} [port]")
            sys.exit(1)
    
    run_server(port)

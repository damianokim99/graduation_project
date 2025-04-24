import requests

# ngrok 주소로 바꾸기
BASE_URL = "https://9ac2-125-179-99-25.ngrok-free.app"

# GET 요청
res1 = requests.get(f"{BASE_URL}/")
print("GET 응답:", res1.json())

# POST 요청
data = {"text": "ngrok 연결 테스트"}
res2 = requests.post(f"{BASE_URL}/echo", json=data)
print("POST 응답:", res2.json())

from locust import HttpUser, task, between


class TicketingUser(HttpUser):
    """
    티켓팅 사용자 시나리오:
    1. 메인 페이지 접속
    2. 대기열 진입 (POST /api/queue/enter)
    3. 순위 폴링 (GET /api/queue/rank) - admitted=true 될 때까지 반복
    4. 입장 후 예매 페이지 접근 + 토큰 검증
    """
    wait_time = between(1, 3)
    token = None
    admitted = False
    booking_done = False

    def on_start(self):
        self.client.get("/", name="01_메인페이지")

        with self.client.post("/api/queue/enter", name="02_대기열진입", catch_response=True) as resp:
            if resp.status_code == 200:
                data = resp.json()
                self.token = data["token"]
                resp.success()
            else:
                resp.failure(f"진입 실패: {resp.status_code}")

    @task
    def poll_rank(self):
        if self.token is None:
            return

        if self.admitted:
            if not self.booking_done:
                self.access_booking()
            return

        with self.client.get(
            f"/api/queue/rank?token={self.token}",
            name="03_순위조회(폴링)",
            catch_response=True
        ) as resp:
            if resp.status_code == 200:
                data = resp.json()
                if data.get("admitted"):
                    self.admitted = True
                resp.success()
            else:
                resp.failure(f"순위 조회 실패: {resp.status_code}")

    def access_booking(self):
        self.client.get(
            f"/booking?token={self.token}",
            name="04_예매페이지"
        )
        self.client.get(
            f"/api/queue/verify?token={self.token}",
            name="05_토큰검증"
        )
        self.booking_done = True

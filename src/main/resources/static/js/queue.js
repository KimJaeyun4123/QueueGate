(function () {
    const token = document.getElementById('token').value;
    const rankEl = document.getElementById('rank');
    const totalEl = document.getElementById('total');
    const etaEl = document.getElementById('eta');
    const statusEl = document.getElementById('status-message');
    const progressFill = document.getElementById('progress-fill');

    let initialRank = null;

    async function pollRank() {
        try {
            const res = await fetch('/api/queue/rank?token=' + token);
            const data = await res.json();

            if (data.admitted) {
                rankEl.textContent = '-';
                statusEl.textContent = '입장이 허용되었습니다! 이동 중...';
                progressFill.style.width = '100%';
                setTimeout(function () {
                    window.location.href = '/booking?token=' + token;
                }, 1000);
                return;
            }

            if (data.rank === null) {
                statusEl.textContent = '유효하지 않은 토큰입니다.';
                return;
            }

            if (initialRank === null) {
                initialRank = data.rank;
            }

            rankEl.textContent = data.rank + '번';
            totalEl.textContent = data.totalWaiting + '명';
            etaEl.textContent = data.estimatedWaitSeconds + '초';

            var progress = Math.max(0, (1 - data.rank / initialRank)) * 100;
            progressFill.style.width = progress + '%';

        } catch (e) {
            console.error('폴링 오류:', e);
        }

        setTimeout(pollRank, 2000);
    }

    pollRank();
})();

const ctx = document.getElementById('deviceChart').getContext('2d');
const deviceChart = new Chart(ctx, {
    type: 'bar',
    data: {
        labels: ['传感器', '控制器', '摄像头'],
        datasets: [{
            label: '设备数量',
            data: [10, 84, 100],
            backgroundColor: ['rgba(255, 99, 132, 0.2)'],
            borderColor: ['rgba(255, 99, 132, 1)'],
            borderWidth: 1
        }]
    },
    options: {
        scales: {
            y: {
                beginAtZero: true
            }
        }
    }
});
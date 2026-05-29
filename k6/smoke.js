import http from 'k6/http';
import { check, sleep } from 'k6'

export const options = {
    vus: 1,
    iterations: 1
};

const BASE_URL = 'http://localhost:9999';

const payload = JSON.stringify({
    id: 'smoke-test-001',
    transaction: {
        amount: 150.0,
        installments: 1,
        requested_at: '2026-03-11T14:30:00Z',
    },
    customer: {
        avg_amount: 200.0,
        tx_count_24h: 2,
        known_merchants: ['merchant-abc'],
    },
    merchant: {
        id: 'merchant-abc',
        mcc: '5411',
        avg_amount: 300.0,
    },
    terminal: {
        is_online: false,
        card_present: true,
        km_from_home: 5.0,
    },
    last_transaction: {
        timestamp: '2026-03-11T10:00:00Z',
        km_from_current: 3.0,
    },
});

const headers = { 'Content-Type': 'application/json' };

export default function () {
    const healthResponse = http.get(`${BASE_URL}/ready`);
    check(healthResponse, {
        'GET /ready returns 200': (r) => r.status === 200,
    });

    const fraudResponse = http.post(`${BASE_URL}/fraud-score`, payload, { headers });
    check(fraudResponse, {
        'POST /fraud-score returns 200': (r) => r.status === 200,
        'response has transaction_id': (r) => JSON.parse(r.body).transaction_id = 'smoke-test-001',
        'response has approved field': (r) => JSON.parse(r.body).approved !== undefined,
        'response has fraud_score': (r) => {
            const score = JSON.parse(r.body).fraud_score;
            return score >= 0 && score <= 1;
        }
    })
}

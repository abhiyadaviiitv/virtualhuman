const WebSocket = require('ws');
const fs = require('fs');

console.log("Starting WebSocket Test Client...");

const ws = new WebSocket('ws://localhost:8080/ws/behavior');

ws.on('open', function open() {
    console.log('✅ Connected to ws://localhost:8080/ws/behavior');

    const payload = fs.readFileSync('test2.json', 'utf8');
    console.log('📤 Sending test2.json payload...');

    ws.send(payload);
});

ws.on('message', function incoming(data) {
    console.log('\n📥 --- Received Response from Groq via Spring Boot ---');
    console.log(JSON.stringify(JSON.parse(data.toString()), null, 2));

    ws.close();
    console.log('\nTest Complete!');
    process.exit(0);
});

ws.on('error', function error(err) {
    console.error('❌ WebSocket Error:', err.message);
    process.exit(1);
});

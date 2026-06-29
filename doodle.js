// doodle.js
#!/usr/bin/env node
'use strict';

const blessed = require('blessed');
const fs = require('fs');
const path = require('path');
const os = require('os');

const RECORD_FILE = path.join(os.homedir(), '.doodle_record.json');

function loadRecord() {
    try { return JSON.parse(fs.readFileSync(RECORD_FILE)).record || 0; } catch { return 0; }
}
function saveRecord(record) {
    fs.writeFileSync(RECORD_FILE, JSON.stringify({ record }));
}

let speed = 50;
if (process.argv.includes('-s') && process.argv.length > process.argv.indexOf('-s')+1) {
    speed = parseInt(process.argv[process.argv.indexOf('-s')+1]) || 50;
}

const screen = blessed.screen({
    smartCSR: true,
    title: 'Doodle Jump',
    fullUnicode: true,
});

const height = screen.height;
const width = screen.width;
if (height < 20 || width < 30) {
    console.log('Terminal too small');
    process.exit(1);
}

let playerY = height - 3;
let playerX = Math.floor(width/2);
let velX = 0, velY = 0;
const gravity = 0.3, jump = -6;
let platforms = [];
let score = 0;
let best = loadRecord();
let gameOver = false;
let frameTime = speed;

for (let i=0; i<8; i++) {
    let x = Math.floor(Math.random() * (width-6)) + 1;
    let y = height - 2 - i*4;
    platforms.push({x, y, type: 0});
}

function draw() {
    screen.clear();
    // Player
    screen.fillRegion('@', playerX, playerY, playerX+1, playerY+1, blessed.colors.yellow, blessed.colors.black);
    // Platforms
    for (const p of platforms) {
        let color = blessed.colors.green;
        if (p.type === 1) color = blessed.colors.green; // moving (bold)
        else if (p.type === 2) color = blessed.colors.red;
        else if (p.type === 3) color = blessed.colors.red;
        for (let i=0; i<5; i++) {
            if (p.x+i >= 0 && p.x+i < width) {
                screen.fillRegion('#', p.x+i, p.y, p.x+i+1, p.y+1, color, blessed.colors.black);
            }
        }
    }
    // Score
    screen.setContent(0, 2, `Score: ${score}`, blessed.colors.cyan);
    screen.setContent(0, Math.floor(width/2)-4, `Best: ${best}`, blessed.colors.cyan);
    if (gameOver) {
        screen.setContent(Math.floor(height/2)-2, Math.floor(width/2)-15, `💀 Game Over! Score: ${score}  Best: ${best}`, blessed.colors.red);
        screen.setContent(Math.floor(height/2), Math.floor(width/2)-10, 'R - restart | Q - quit', blessed.colors.cyan);
    }
    screen.render();
}

function update() {
    if (gameOver) {
        draw();
        return;
    }

    // Physics
    velY += gravity;
    playerY += velY;
    playerX += velX;
    if (playerX < 0) playerX = 0;
    if (playerX >= width) playerX = width - 1;

    // Collision
    for (const p of platforms) {
        if (p.x <= playerX && playerX <= p.x+4 &&
            p.y <= playerY+1 && playerY+1 <= p.y+1 &&
            velY > 0) {
            velY = jump;
            process.stdout.write('\x07');
            if (p.type === 3) {
                score += 10;
                p.type = -1;
            } else if (p.type === 2) {
                p.type = -1;
            }
            break;
        }
    }
    platforms = platforms.filter(p => p.type !== -1);

    // Generate new platforms
    if (playerY < height/2) {
        const diff = height/2 - playerY;
        playerY += diff;
        for (const p of platforms) p.y += diff;
        while (platforms.length > 0 && platforms[platforms.length-1].y < height-2) {
            let x = Math.floor(Math.random() * (width-6)) + 1;
            let y = platforms[platforms.length-1].y - (Math.floor(Math.random()*4)+3);
            let r = Math.random()*100;
            let type = 0;
            if (r < 70) type = 0;
            else if (r < 85) type = 1;
            else if (r < 95) type = 2;
            else type = 3;
            platforms.push({x, y, type});
            score++;
        }
    }

    if (playerY > height) {
        gameOver = true;
        if (score > best) { best = score; saveRecord(best); }
    }
    draw();
    setTimeout(update, frameTime);
}

// Controls
screen.key(['left', 'a', 'A'], function() { velX = -3; });
screen.key(['right', 'd', 'D'], function() { velX = 3; });
screen.key(['space'], function() { velY = 2; });
screen.key(['r', 'R'], function() {
    if (gameOver) {
        playerY = height - 3;
        playerX = Math.floor(width/2);
        velX = 0; velY = 0;
        platforms = [];
        score = 0;
        gameOver = false;
        for (let i=0; i<8; i++) {
            let x = Math.floor(Math.random() * (width-6)) + 1;
            let y = height - 2 - i*4;
            platforms.push({x, y, type: 0});
        }
        draw();
    }
});
screen.key(['q', 'Q'], function() { process.exit(0); });

draw();
update();

screen.on('resize', function() {});

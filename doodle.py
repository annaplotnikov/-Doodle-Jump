# doodle.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os
import random
import json
import time
import argparse
import curses
from pathlib import Path

# Конфигурация
RECORD_FILE = Path.home() / '.doodle_record.json'

def load_record():
    try:
        with open(RECORD_FILE, 'r') as f:
            return json.load(f).get('record', 0)
    except:
        return 0

def save_record(record):
    with open(RECORD_FILE, 'w') as f:
        json.dump({'record': record}, f)

def main(stdscr, speed):
    curses.curs_set(0)
    stdscr.nodelay(1)
    stdscr.timeout(0)
    curses.start_color()
    curses.use_default_colors()
    curses.init_pair(1, curses.COLOR_YELLOW, -1)   # игрок
    curses.init_pair(2, curses.COLOR_GREEN, -1)   # платформы
    curses.init_pair(3, curses.COLOR_RED, -1)     # опасность/монета
    curses.init_pair(4, curses.COLOR_CYAN, -1)    # счёт

    height, width = stdscr.getmaxyx()
    if height < 20 or width < 30:
        print("Терминал слишком мал. Нужно минимум 20x30.")
        return

    # Игровые параметры
    player_y = height - 3
    player_x = width // 2
    vel_x = 0
    vel_y = 0
    gravity = 0.3
    jump_strength = -6
    platforms = []
    score = 0
    best = load_record()
    game_over = False
    frame_time = speed / 1000.0

    # Генерация начальных платформ
    for i in range(8):
        x = random.randint(1, width-6)
        y = height - 2 - i * 4
        platforms.append([x, y, 0])  # x, y, тип (0=обычная)

    # Основной цикл
    while True:
        key = stdscr.getch()
        if key == ord('q') or key == ord('Q'):
            break
        if key == ord('r') or key == ord('R'):
            if game_over:
                player_y = height - 3
                player_x = width // 2
                vel_x = 0
                vel_y = 0
                platforms = []
                score = 0
                game_over = False
                for i in range(8):
                    x = random.randint(1, width-6)
                    y = height - 2 - i * 4
                    platforms.append([x, y, 0])
                continue

        if game_over:
            stdscr.clear()
            msg = f"💀 Игра окончена! Счёт: {score}  Рекорд: {best}"
            stdscr.addstr(height//2 - 2, (width - len(msg))//2, msg, curses.color_pair(3))
            stdscr.addstr(height//2, (width - 20)//2, "R - рестарт | Q - выход", curses.color_pair(4))
            stdscr.refresh()
            continue

        # Управление
        if key == curses.KEY_LEFT or key == ord('a') or key == ord('A'):
            vel_x = -3
        elif key == curses.KEY_RIGHT or key == ord('d') or key == ord('D'):
            vel_x = 3
        else:
            vel_x *= 0.85  # трение

        # Прыжок (ускоренное падение)
        if key == ord(' '):
            vel_y = 2  # ускорение вниз

        # Физика
        vel_y += gravity
        player_y += vel_y
        player_x += vel_x

        # Границы
        if player_x < 0: player_x = 0
        if player_x >= width: player_x = width - 1

        # Столкновение с платформами
        player_bottom = player_y + 1
        for plat in platforms:
            px, py, ptype = plat
            if (px <= player_x <= px + 4 and
                py <= player_bottom <= py + 1 and
                vel_y > 0):
                vel_y = jump_strength
                # звук
                stdscr.addstr(0,0,'\a')
                if ptype == 1:  # движущаяся
                    pass  # просто прыжок
                elif ptype == 2:  # исчезающая
                    plat[2] = -1  # пометить на удаление
                # монетка
                if ptype == 3:
                    score += 10
                    plat[2] = -1
                break

        # Удаление платформ
        platforms = [p for p in platforms if p[2] != -1]

        # Генерация новых платформ при подъёме
        if player_y < height // 2:
            diff = height // 2 - player_y
            player_y += diff
            for plat in platforms:
                plat[1] += diff
            # Добавляем новые платформы вверху
            while platforms and platforms[-1][1] < height - 2:
                x = random.randint(1, width-6)
                y = platforms[-1][1] - random.randint(3, 6)
                ptype = random.choices([0, 1, 2, 3], weights=[70, 15, 10, 5])[0]
                platforms.append([x, y, ptype])
                # Счёт за каждую новую платформу
                score += 1

        # Проверка падения
        if player_y > height:
            game_over = True
            if score > best:
                best = score
                save_record(best)

        # Отрисовка
        stdscr.clear()
        # Игрок
        stdscr.addstr(int(player_y), int(player_x), '@', curses.color_pair(1) | curses.A_BOLD)
        # Платформы
        for plat in platforms:
            px, py, ptype = plat
            if ptype == 1:
                color = curses.color_pair(2) | curses.A_BOLD  # движущаяся
            elif ptype == 2:
                color = curses.color_pair(3)  # исчезающая
            elif ptype == 3:
                color = curses.color_pair(3)  # монетка
            else:
                color = curses.color_pair(2)
            for i in range(5):
                if 0 <= px+i < width:
                    stdscr.addch(int(py), int(px+i), '#', color)
        # Счёт
        stdscr.addstr(0, 2, f"Счёт: {score}", curses.color_pair(4))
        stdscr.addstr(0, width//2 - 4, f"Рекорд: {best}", curses.color_pair(4))
        stdscr.refresh()

        time.sleep(frame_time)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-s', '--speed', type=int, default=50, help='Скорость (мс)')
    args = parser.parse_args()
    try:
        curses.wrapper(main, args.speed)
    except KeyboardInterrupt:
        print("\nИгра завершена.")

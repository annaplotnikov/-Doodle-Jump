// doodle.cpp
#include <curses.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>
#include <fstream>
#include <string>
#include <vector>
#include <json/json.h>

using namespace std;

int loadRecord() {
    ifstream f(getenv("HOME") + string("/.doodle_record.json"));
    Json::Value root;
    if (f >> root) return root["record"].asInt();
    return 0;
}

void saveRecord(int record) {
    Json::Value root;
    root["record"] = record;
    ofstream f(getenv("HOME") + string("/.doodle_record.json"));
    f << root.toStyledString();
}

int main(int argc, char* argv[]) {
    int speed = 50;
    for (int i=1; i<argc; ++i) {
        if (string(argv[i]) == "-s" && i+1 < argc) speed = atoi(argv[++i]);
        else if (string(argv[i]) == "-h") { cout << "Usage: doodle [-s speed_ms]\n"; return 0; }
    }

    initscr();
    cbreak();
    noecho();
    curs_set(0);
    nodelay(stdscr, TRUE);
    keypad(stdscr, TRUE);
    start_color();
    init_pair(1, COLOR_YELLOW, COLOR_BLACK);
    init_pair(2, COLOR_GREEN, COLOR_BLACK);
    init_pair(3, COLOR_RED, COLOR_BLACK);
    init_pair(4, COLOR_CYAN, COLOR_BLACK);

    int height, width;
    getmaxyx(stdscr, height, width);
    if (height < 20 || width < 30) {
        endwin();
        cout << "Terminal too small.\n";
        return 1;
    }

    float player_y = height - 3;
    float player_x = width / 2;
    float vel_x = 0, vel_y = 0;
    const float gravity = 0.3, jump = -6;
    struct Platform { int x, y, type; };
    vector<Platform> platforms;
    int score = 0, best = loadRecord();
    bool gameOver = false;
    int frameTime = speed * 1000;

    // Initial platforms
    for (int i=0; i<8; ++i) {
        int x = rand() % (width-6) + 1;
        int y = height - 2 - i*4;
        platforms.push_back({x, y, 0});
    }

    while (true) {
        int ch = getch();
        if (ch == 'q' || ch == 'Q') break;
        if (ch == 'r' || ch == 'R') {
            if (gameOver) {
                player_y = height - 3; player_x = width/2;
                vel_x = 0; vel_y = 0;
                platforms.clear();
                score = 0; gameOver = false;
                for (int i=0; i<8; ++i) {
                    int x = rand() % (width-6) + 1;
                    int y = height - 2 - i*4;
                    platforms.push_back({x, y, 0});
                }
                continue;
            }
        }

        if (gameOver) {
            clear();
            string msg = "💀 Game Over! Score: " + to_string(score) + "  Best: " + to_string(best);
            mvprintw(height/2-2, (width-msg.length())/2, "%s", msg.c_str());
            mvprintw(height/2, (width-20)/2, "R - restart | Q - quit");
            refresh();
            continue;
        }

        // Controls
        if (ch == KEY_LEFT || ch == 'a' || ch == 'A') vel_x = -3;
        else if (ch == KEY_RIGHT || ch == 'd' || ch == 'D') vel_x = 3;
        else vel_x *= 0.85;
        if (ch == ' ') vel_y = 2; // fall faster

        // Physics
        vel_y += gravity;
        player_y += vel_y;
        player_x += vel_x;

        if (player_x < 0) player_x = 0;
        if (player_x >= width) player_x = width - 1;

        // Collision with platforms
        for (auto &p : platforms) {
            if (p.x <= player_x && player_x <= p.x+4 &&
                p.y <= player_y+1 && player_y+1 <= p.y+1 &&
                vel_y > 0) {
                vel_y = jump;
                putchar('\a');
                if (p.type == 1) {} // moving
                else if (p.type == 2 || p.type == 3) { // disappear or coin
                    p.type = -1; // mark for removal
                    if (p.type == 3) score += 10;
                }
                break;
            }
        }

        // Remove marked platforms
        platforms.erase(remove_if(platforms.begin(), platforms.end(),
                                  [](Platform p){ return p.type == -1; }), platforms.end());

        // Generate new platforms when player goes up
        if (player_y < height/2) {
            float diff = height/2 - player_y;
            player_y += diff;
            for (auto &p : platforms) p.y += diff;
            while (!platforms.empty() && platforms.back().y < height-2) {
                int x = rand() % (width-6) + 1;
                int y = platforms.back().y - (rand()%4+3);
                int type = rand() % 100;
                int ptype = (type < 70) ? 0 : (type < 85) ? 1 : (type < 95) ? 2 : 3;
                platforms.push_back({x, y, ptype});
                score++;
            }
        }

        if (player_y > height) {
            gameOver = true;
            if (score > best) { best = score; saveRecord(best); }
        }

        // Draw
        clear();
        attron(COLOR_PAIR(1) | A_BOLD);
        mvaddch((int)player_y, (int)player_x, '@');
        attroff(COLOR_PAIR(1) | A_BOLD);
        attron(COLOR_PAIR(2));
        for (auto &p : platforms) {
            if (p.type == 1) attron(COLOR_PAIR(2) | A_BOLD);
            else if (p.type == 2 || p.type == 3) attron(COLOR_PAIR(3));
            for (int i=0; i<5; ++i) {
                if (p.x+i >= 0 && p.x+i < width)
                    mvaddch(p.y, p.x+i, '#');
            }
            attroff(COLOR_PAIR(2) | A_BOLD | COLOR_PAIR(3));
        }
        attron(COLOR_PAIR(4));
        mvprintw(0, 2, "Score: %d", score);
        mvprintw(0, width/2-4, "Best: %d", best);
        attroff(COLOR_PAIR(4));
        refresh();
        usleep(frameTime);
    }
    endwin();
    return 0;
}

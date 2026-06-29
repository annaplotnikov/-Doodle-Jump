// doodle.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Threading;
using System.Runtime.InteropServices;

class DoodleJump
{
    static string Colorize(string text, string color)
    {
        string col = color switch
        {
            "yellow" => "\x1b[93m",
            "green" => "\x1b[92m",
            "red" => "\x1b[91m",
            "cyan" => "\x1b[96m",
            _ => "\x1b[0m"
        };
        return col + text + "\x1b[0m";
    }

    static string ConfigFile => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".doodle_record.json");

    static int LoadRecord()
    {
        if (!File.Exists(ConfigFile)) return 0;
        string json = File.ReadAllText(ConfigFile);
        var data = JsonSerializer.Deserialize<Dictionary<string,int>>(json);
        return data.GetValueOrDefault("record", 0);
    }

    static void SaveRecord(int record)
    {
        var data = new Dictionary<string,int> { { "record", record } };
        string json = JsonSerializer.Serialize(data);
        File.WriteAllText(ConfigFile, json);
    }

    static void Main(string[] args)
    {
        int speed = 50;
        for (int i=0; i<args.Length; i++)
        {
            if (args[i] == "-s" && i+1 < args.Length)
                speed = int.Parse(args[++i]);
            else if (args[i] == "-h") { Console.WriteLine("Usage: doodle [-s speed_ms]"); return; }
        }

        Console.Clear();
        int height = Console.WindowHeight;
        int width = Console.WindowWidth;
        if (height < 20 || width < 30)
        {
            Console.WriteLine("Terminal too small");
            return;
        }

        Random rand = new Random();
        float playerY = height - 3;
        float playerX = width / 2;
        float velX = 0, velY = 0;
        const float gravity = 0.3f, jump = -6f;
        List<(int x, int y, int type)> platforms = new List<(int, int, int)>();
        int score = 0, best = LoadRecord();
        bool gameOver = false;
        int frameTime = speed;

        // initial platforms
        for (int i=0; i<8; i++)
        {
            int x = rand.Next(1, width-6);
            int y = height - 2 - i*4;
            platforms.Add((x, y, 0));
        }

        Console.CursorVisible = false;

        while (true)
        {
            if (Console.KeyAvailable)
            {
                var key = Console.ReadKey(true).Key;
                if (key == ConsoleKey.Q) break;
                if (key == ConsoleKey.R && gameOver)
                {
                    playerY = height - 3; playerX = width/2;
                    velX = 0; velY = 0;
                    platforms.Clear(); score = 0; gameOver = false;
                    for (int i=0; i<8; i++) { int x = rand.Next(1, width-6); int y = height - 2 - i*4; platforms.Add((x, y, 0)); }
                    continue;
                }
                if (key == ConsoleKey.LeftArrow || key == ConsoleKey.A) velX = -3;
                else if (key == ConsoleKey.RightArrow || key == ConsoleKey.D) velX = 3;
                else velX *= 0.85f;
                if (key == ConsoleKey.Spacebar) velY = 2;
            }

            if (gameOver)
            {
                Console.Clear();
                string msg = $"💀 Game Over! Score: {score}  Best: {best}";
                Console.SetCursorPosition((width - msg.Length) / 2, height / 2 - 2);
                Console.Write(Colorize(msg, "red"));
                Console.SetCursorPosition((width - 20) / 2, height / 2);
                Console.Write(Colorize("R - restart | Q - quit", "cyan"));
                continue;
            }

            // Physics
            velY += gravity;
            playerY += velY;
            playerX += velX;
            if (playerX < 0) playerX = 0;
            if (playerX >= width) playerX = width - 1;

            // Collision
            for (int i=0; i<platforms.Count; i++)
            {
                var p = platforms[i];
                if (p.x <= playerX && playerX <= p.x+4 &&
                    p.y <= playerY+1 && playerY+1 <= p.y+1 &&
                    velY > 0)
                {
                    velY = jump;
                    Console.Beep();
                    if (p.type == 3) { score += 10; platforms[i] = (p.x, p.y, -1); }
                    else if (p.type == 2) { platforms[i] = (p.x, p.y, -1); }
                    break;
                }
            }
            platforms.RemoveAll(p => p.type == -1);

            // Generate new
            if (playerY < height/2)
            {
                float diff = height/2 - playerY;
                playerY += diff;
                for (int i=0; i<platforms.Count; i++)
                {
                    var p = platforms[i];
                    platforms[i] = (p.x, (int)(p.y + diff), p.type);
                }
                while (platforms.Count > 0 && platforms[platforms.Count-1].y < height-2)
                {
                    int x = rand.Next(1, width-6);
                    int y = platforms[platforms.Count-1].y - (rand.Next(4)+3);
                    int r = rand.Next(100);
                    int type = r < 70 ? 0 : (r < 85 ? 1 : (r < 95 ? 2 : 3));
                    platforms.Add((x, y, type));
                    score++;
                }
            }

            if (playerY > height)
            {
                gameOver = true;
                if (score > best) { best = score; SaveRecord(best); }
            }

            // Draw
            Console.Clear();
            // player
            Console.SetCursorPosition((int)playerX, (int)playerY);
            Console.Write(Colorize("@", "yellow"));
            // platforms
            foreach (var p in platforms)
            {
                string color = "green";
                if (p.type == 1) color = "green"; // bold
                else if (p.type == 2 || p.type == 3) color = "red";
                for (int i=0; i<5; i++)
                {
                    if (p.x+i >= 0 && p.x+i < width)
                    {
                        Console.SetCursorPosition(p.x+i, p.y);
                        Console.Write(Colorize("#", color));
                    }
                }
            }
            // score
            Console.SetCursorPosition(2, 0);
            Console.Write(Colorize($"Score: {score}", "cyan"));
            Console.SetCursorPosition(width/2-4, 0);
            Console.Write(Colorize($"Best: {best}", "cyan"));
            Thread.Sleep(frameTime);
        }
    }
}

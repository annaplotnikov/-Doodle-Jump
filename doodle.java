// doodle.java
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public class doodle {
    private static String configFile = System.getProperty("user.home") + "/.doodle_record.json";

    private static int loadRecord() throws IOException {
        Path path = Paths.get(configFile);
        if (!Files.exists(path)) return 0;
        String json = new String(Files.readAllBytes(path));
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        return obj.get("record").getAsInt();
    }

    private static void saveRecord(int record) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject obj = new JsonObject();
        obj.addProperty("record", record);
        Files.write(Paths.get(configFile), gson.toJson(obj).getBytes());
    }

    public static void main(String[] args) throws Exception {
        int speed = 50;
        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-s") && i+1 < args.length) {
                speed = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-h")) {
                System.out.println("Usage: doodle [-s speed_ms]");
                return;
            }
        }

        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        terminal.enterPrivateMode();
        terminal.setCursorVisible(false);
        TerminalSize size = terminal.getTerminalSize();
        int height = size.getRows();
        int width = size.getColumns();
        if (height < 20 || width < 30) {
            System.out.println("Terminal too small");
            System.exit(1);
        }

        Random rand = new Random();
        float playerY = height - 3;
        float playerX = width / 2;
        float velX = 0, velY = 0;
        final float gravity = 0.3f, jump = -6f;
        List<int[]> platforms = new ArrayList<>();
        int score = 0, best = loadRecord();
        boolean gameOver = false;
        int frameTime = speed;

        // initial platforms
        for (int i=0; i<8; i++) {
            int x = rand.nextInt(width-6) + 1;
            int y = height - 2 - i*4;
            platforms.add(new int[]{x, y, 0});
        }

        TextGraphics tg = terminal.newTextGraphics();

        while (true) {
            KeyStroke key = terminal.pollInput();
            if (key != null) {
                char ch = key.getCharacter() != null ? key.getCharacter() : 0;
                if (ch == 'q' || ch == 'Q') break;
                if (ch == 'r' || ch == 'R') {
                    if (gameOver) {
                        playerY = height - 3; playerX = width/2;
                        velX = 0; velY = 0;
                        platforms.clear();
                        score = 0; gameOver = false;
                        for (int i=0; i<8; i++) {
                            int x = rand.nextInt(width-6) + 1;
                            int y = height - 2 - i*4;
                            platforms.add(new int[]{x, y, 0});
                        }
                        continue;
                    }
                }
                if (key.getKeyType() == KeyStroke.KeyType.ArrowLeft || ch == 'a') velX = -3;
                else if (key.getKeyType() == KeyStroke.KeyType.ArrowRight || ch == 'd') velX = 3;
                else velX *= 0.85;
                if (ch == ' ') velY = 2;
            }

            if (gameOver) {
                tg.clear();
                String msg = "💀 Game Over! Score: " + score + "  Best: " + best;
                tg.putString((width - msg.length())/2, height/2-2, msg, TextColor.ANSI.RED);
                tg.putString((width - 20)/2, height/2, "R - restart | Q - quit", TextColor.ANSI.CYAN);
                terminal.flush();
                continue;
            }

            // Physics
            velY += gravity;
            playerY += velY;
            playerX += velX;
            if (playerX < 0) playerX = 0;
            if (playerX >= width) playerX = width - 1;

            // Collision
            for (int[] p : platforms) {
                if (p[0] <= playerX && playerX <= p[0]+4 &&
                    p[1] <= playerY+1 && playerY+1 <= p[1]+1 &&
                    velY > 0) {
                    velY = jump;
                    System.out.print("\007");
                    if (p[2] == 3) {
                        score += 10;
                        p[2] = -1;
                    } else if (p[2] == 2) {
                        p[2] = -1;
                    }
                    break;
                }
            }
            platforms.removeIf(p -> p[2] == -1);

            // Generate new
            if (playerY < height/2) {
                float diff = height/2 - playerY;
                playerY += diff;
                for (int[] p : platforms) p[1] += (int)diff;
                while (!platforms.isEmpty() && platforms.get(platforms.size()-1)[1] < height-2) {
                    int x = rand.nextInt(width-6) + 1;
                    int y = platforms.get(platforms.size()-1)[1] - (rand.nextInt(4)+3);
                    int r = rand.nextInt(100);
                    int type = r < 70 ? 0 : (r < 85 ? 1 : (r < 95 ? 2 : 3));
                    platforms.add(new int[]{x, y, type});
                    score++;
                }
            }

            if (playerY > height) {
                gameOver = true;
                if (score > best) { best = score; saveRecord(best); }
            }

            // Draw
            tg.clear();
            // player
            tg.putString((int)playerX, (int)playerY, "@", TextColor.ANSI.YELLOW, TextColor.ANSI.DEFAULT);
            // platforms
            for (int[] p : platforms) {
                TextColor color = TextColor.ANSI.GREEN;
                if (p[2] == 1) color = TextColor.ANSI.GREEN; // bold
                else if (p[2] == 2 || p[2] == 3) color = TextColor.ANSI.RED;
                for (int i=0; i<5; i++) {
                    if (p[0]+i >= 0 && p[0]+i < width) {
                        tg.putString(p[0]+i, p[1], "#", color);
                    }
                }
            }
            // score
            tg.putString(2, 0, "Score: " + score, TextColor.ANSI.CYAN);
            tg.putString(width/2-4, 0, "Best: " + best, TextColor.ANSI.CYAN);
            terminal.flush();

            Thread.sleep(frameTime);
        }
        terminal.exitPrivateMode();
        terminal.close();
    }
}

#!/usr/bin/env ruby
# doodle.rb
# encoding: UTF-8

require 'curses'
require 'json'
require 'fileutils'

RECORD_FILE = File.join(Dir.home, '.doodle_record.json')

def load_record
  return 0 unless File.exist?(RECORD_FILE)
  JSON.parse(File.read(RECORD_FILE))['record'] || 0
rescue
  0
end

def save_record(record)
  File.write(RECORD_FILE, JSON.pretty_generate(record: record))
end

Curses.init_screen
Curses.start_color
Curses.use_default_colors
Curses.init_pair(1, Curses::COLOR_YELLOW, -1)
Curses.init_pair(2, Curses::COLOR_GREEN, -1)
Curses.init_pair(3, Curses::COLOR_RED, -1)
Curses.init_pair(4, Curses::COLOR_CYAN, -1)

height = Curses.lines
width = Curses.cols
if height < 20 || width < 30
  puts "Terminal too small"
  exit 1
end

speed = 50
if ARGV.include?('-s') && ARGV.index('-s') + 1 < ARGV.size
  speed = ARGV[ARGV.index('-s') + 1].to_i
end

player_y = height - 3
player_x = width / 2
vel_x = 0.0
vel_y = 0.0
gravity = 0.3
jump = -6.0
platforms = []
score = 0
best = load_record
game_over = false
frame_time = speed / 1000.0

8.times do
  x = rand(1..width-6)
  y = height - 2 - rand(0..3)*4
  platforms << [x, y, 0]
end

Curses.curs_set(0)
Curses.noecho
Curses.timeout = 0

loop do
  ch = Curses.getch
  if ch == 'q' || ch == 'Q'
    break
  elsif ch == 'r' || ch == 'R'
    if game_over
      player_y = height - 3
      player_x = width / 2
      vel_x = 0
      vel_y = 0
      platforms = []
      score = 0
      game_over = false
      8.times do
        x = rand(1..width-6)
        y = height - 2 - rand(0..3)*4
        platforms << [x, y, 0]
      end
      next
    end
  end

  if game_over
    Curses.clear
    msg = "💀 Game Over! Score: #{score}  Best: #{best}"
    Curses.setpos(height/2-2, (width - msg.length)/2)
    Curses.attron(Curses.color_pair(3)) { Curses.addstr(msg) }
    Curses.setpos(height/2, (width - 20)/2)
    Curses.attron(Curses.color_pair(4)) { Curses.addstr("R - restart | Q - quit") }
    Curses.refresh
    next
  end

  # Controls
  if ch == Curses::KEY_LEFT || ch == 'a'
    vel_x = -3
  elsif ch == Curses::KEY_RIGHT || ch == 'd'
    vel_x = 3
  else
    vel_x *= 0.85
  end
  vel_y = 2 if ch == ' '

  # Physics
  vel_y += gravity
  player_y += vel_y
  player_x += vel_x
  player_x = 0 if player_x < 0
  player_x = width - 1 if player_x >= width

  # Collision
  platforms.each do |p|
    if p[0] <= player_x && player_x <= p[0]+4 &&
       p[1] <= player_y+1 && player_y+1 <= p[1]+1 &&
       vel_y > 0
      vel_y = jump
      print "\a"
      if p[2] == 3
        score += 10
        p[2] = -1
      elsif p[2] == 2
        p[2] = -1
      end
      break
    end
  end
  platforms.delete_if { |p| p[2] == -1 }

  # Generate new
  if player_y < height/2
    diff = height/2 - player_y
    player_y += diff
    platforms.each { |p| p[1] += diff }
    while platforms.last && platforms.last[1] < height-2
      x = rand(1..width-6)
      y = platforms.last[1] - (rand(4)+3)
      r = rand(100)
      type = r < 70 ? 0 : (r < 85 ? 1 : (r < 95 ? 2 : 3))
      platforms << [x, y, type]
      score += 1
    end
  end

  if player_y > height
    game_over = true
    if score > best
      best = score
      save_record(best)
    end
  end

  # Draw
  Curses.clear
  Curses.attron(Curses.color_pair(1) | Curses::A_BOLD) do
    Curses.setpos(player_y.to_i, player_x.to_i)
    Curses.addstr('@')
  end
  platforms.each do |p|
    color = case p[2]
            when 1 then Curses.color_pair(2) | Curses::A_BOLD
            when 2,3 then Curses.color_pair(3)
            else Curses.color_pair(2)
            end
    Curses.attron(color) do
      5.times do |i|
        if p[0]+i >= 0 && p[0]+i < width
          Curses.setpos(p[1], p[0]+i)
          Curses.addstr('#')
        end
      end
    end
  end
  Curses.attron(Curses.color_pair(4)) do
    Curses.setpos(0, 2)
    Curses.addstr("Score: #{score}")
    Curses.setpos(0, width/2-4)
    Curses.addstr("Best: #{best}")
  end
  Curses.refresh
  sleep(frame_time)
end

Curses.close_screen

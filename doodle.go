// doodle.go
package main

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"time"
	"github.com/nsf/termbox-go"
)

const recordFile = ".doodle_record.json"

type Record struct {
	Best int `json:"record"`
}

func loadRecord() int {
	f, err := os.Open(recordFile)
	if err != nil {
		return 0
	}
	defer f.Close()
	var r Record
	json.NewDecoder(f).Decode(&r)
	return r.Best
}

func saveRecord(best int) {
	r := Record{Best: best}
	f, _ := os.Create(recordFile)
	defer f.Close()
	json.NewEncoder(f).Encode(r)
}

func main() {
	speed := 50
	if len(os.Args) > 2 && os.Args[1] == "-s" {
		if s, err := strconv.Atoi(os.Args[2]); err == nil && s > 0 {
			speed = s
		}
	}
	err := termbox.Init()
	if err != nil {
		fmt.Println("termbox init failed:", err)
		return
	}
	defer termbox.Close()
	termbox.SetInputMode(termbox.InputEsc)
	w, h := termbox.Size()
	if h < 20 || w < 30 {
		fmt.Println("Terminal too small")
		return
	}
	rand.Seed(time.Now().UnixNano())

	playerY := float64(h - 3)
	playerX := float64(w / 2)
	velX, velY := 0.0, 0.0
	const gravity, jump = 0.3, -6.0
	type Platform struct{ x, y, typ int }
	platforms := []Platform{}
	score, best := 0, loadRecord()
	gameOver := false
	frame := time.Duration(speed) * time.Millisecond

	// initial platforms
	for i := 0; i < 8; i++ {
		x := rand.Intn(w-6) + 1
		y := h - 2 - i*4
		platforms = append(platforms, Platform{x, y, 0})
	}

	for {
		ev := termbox.PollEvent()
		if ev.Type == termbox.EventKey {
			if ev.Key == termbox.KeyEsc || ev.Ch == 'q' {
				return
			}
			if ev.Ch == 'r' {
				if gameOver {
					playerY = float64(h - 3)
					playerX = float64(w / 2)
					velX, velY = 0, 0
					platforms = []Platform{}
					score = 0
					gameOver = false
					for i := 0; i < 8; i++ {
						x := rand.Intn(w-6) + 1
						y := h - 2 - i*4
						platforms = append(platforms, Platform{x, y, 0})
					}
					continue
				}
			}
			if ev.Key == termbox.KeyArrowLeft || ev.Ch == 'a' {
				velX = -3
			} else if ev.Key == termbox.KeyArrowRight || ev.Ch == 'd' {
				velX = 3
			} else {
				velX *= 0.85
			}
			if ev.Ch == ' ' {
				velY = 2
			}
		}

		if gameOver {
			termbox.Clear(termbox.ColorDefault, termbox.ColorDefault)
			msg := fmt.Sprintf("💀 Game Over! Score: %d  Best: %d", score, best)
			tbprint(w/2-len(msg)/2, h/2-2, termbox.ColorRed, termbox.ColorDefault, msg)
			tbprint(w/2-10, h/2, termbox.ColorCyan, termbox.ColorDefault, "R - restart | Q - quit")
			termbox.Flush()
			continue
		}

		// Physics
		velY += gravity
		playerY += velY
		playerX += velX
		if playerX < 0 {
			playerX = 0
		}
		if playerX >= float64(w) {
			playerX = float64(w - 1)
		}

		// Collision
		for i := range platforms {
			p := &platforms[i]
			if float64(p.x) <= playerX && playerX <= float64(p.x+4) &&
				float64(p.y) <= playerY+1 && playerY+1 <= float64(p.y+1) &&
				velY > 0 {
				velY = jump
				fmt.Print("\a")
				if p.typ == 3 {
					score += 10
					p.typ = -1
				} else if p.typ == 2 {
					p.typ = -1
				}
				break
			}
		}
		// remove marked
		newPlats := []Platform{}
		for _, p := range platforms {
			if p.typ != -1 {
				newPlats = append(newPlats, p)
			}
		}
		platforms = newPlats

		// generate new platforms
		if playerY < float64(h/2) {
			diff := float64(h/2) - playerY
			playerY += diff
			for i := range platforms {
				platforms[i].y += int(diff)
			}
			for len(platforms) > 0 && platforms[len(platforms)-1].y < h-2 {
				x := rand.Intn(w-6) + 1
				y := platforms[len(platforms)-1].y - (rand.Intn(4) + 3)
				typ := rand.Intn(100)
				ptype := 0
				if typ < 70 {
					ptype = 0
				} else if typ < 85 {
					ptype = 1
				} else if typ < 95 {
					ptype = 2
				} else {
					ptype = 3
				}
				platforms = append(platforms, Platform{x, y, ptype})
				score++
			}
		}

		if playerY > float64(h) {
			gameOver = true
			if score > best {
				best = score
				saveRecord(best)
			}
		}

		// Draw
		termbox.Clear(termbox.ColorDefault, termbox.ColorDefault)
		// player
		termbox.SetCell(int(playerX), int(playerY), '@', termbox.ColorYellow|termbox.AttrBold, termbox.ColorDefault)
		// platforms
		for _, p := range platforms {
			var fg termbox.Attribute
			switch p.typ {
			case 1:
				fg = termbox.ColorGreen | termbox.AttrBold
			case 2, 3:
				fg = termbox.ColorRed
			default:
				fg = termbox.ColorGreen
			}
			for i := 0; i < 5; i++ {
				if p.x+i >= 0 && p.x+i < w {
					termbox.SetCell(p.x+i, p.y, '#', fg, termbox.ColorDefault)
				}
			}
		}
		// score
		tbprint(2, 0, termbox.ColorCyan, termbox.ColorDefault, fmt.Sprintf("Score: %d", score))
		tbprint(w/2-4, 0, termbox.ColorCyan, termbox.ColorDefault, fmt.Sprintf("Best: %d", best))
		termbox.Flush()
		time.Sleep(frame)
	}
}

func tbprint(x, y int, fg, bg termbox.Attribute, msg string) {
	for _, ch := range msg {
		termbox.SetCell(x, y, ch, fg, bg)
		x++
	}
}

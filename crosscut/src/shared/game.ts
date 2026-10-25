import {
  body,
  DT,
  EYE,
  HEIGHT,
  idleInput,
  move,
  spray,
  type Input,
} from './physics'
import { direction, rayBox, spawns, wallDistance, waypoints } from './world'
import { route } from './navigation'
import type { Vec3 } from './world'
import type { GameEvent, Player, Snapshot, Stats } from './protocol'
type Actor = {
  p: Player
  input: Input
  queue: Input[]
  lastSeq: number
  nextShot: number
  lastFire: number
  target: number
  stuck: number
  path: Vec3[]
  nextRoute: number
  joined: number
  earned: Stats
}
export class Game {
  actors = new Map<string, Actor>()
  events: GameEvent[] = []
  time = 0
  roundEnds = 240
  restartAt = 0
  roundId = crypto.randomUUID()
  onResult?: (id: string, stats: Stats) => void
  add(id: string, name: string, bot = false) {
    if (this.actors.has(id)) return this.actors.get(id)!.p
    const a: Actor = {
      p: {
        ...body(),
        id,
        name,
        yaw: 0,
        pitch: 0,
        health: 100,
        kills: 0,
        deaths: 0,
        weapon: 'rifle',
        ammo: 30,
        reloadUntil: 0,
        respawnAt: 0,
        protectedUntil: 0,
        ack: 0,
        bot,
        shot: 0,
      },
      input: idleInput(),
      queue: [],
      lastSeq: 0,
      nextShot: 0,
      lastFire: 0,
      target: Math.floor(Math.random() * waypoints.length),
      stuck: 0,
      path: [],
      nextRoute: 0,
      joined: this.time,
      earned: { kills: 0, deaths: 0, wins: 0, matches: 0 },
    }
    this.actors.set(id, a)
    this.spawn(a)
    return a.p
  }
  remove(id: string) {
    const a = this.actors.get(id)
    if (a) this.save(a, false)
    this.actors.delete(id)
  }
  enqueue(id: string, inputs: Input[]) {
    const a = this.actors.get(id)
    if (!a) return
    for (const i of inputs) {
      if (i.seq <= a.lastSeq || i.seq > a.lastSeq + 512 || a.queue.length >= 12)
        continue
      a.lastSeq = i.seq
      a.queue.push(i)
    }
  }
  spawn(a: Actor) {
    const choices = spawns
      .map((s) => ({
        s,
        d: Math.min(
          1e3,
          ...[...this.actors.values()]
            .filter((o) => o !== a && o.p.health > 0)
            .map((o) => Math.hypot(o.p.x - s.x, o.p.z - s.z)),
        ),
      }))
      .sort((a, b) => b.d - a.d)
    const s = choices[Math.floor(Math.random() * Math.min(2, choices.length))].s
    Object.assign(a.p, body(s.x, s.y, s.z), {
      yaw: s.yaw,
      pitch: 0,
      health: 100,
      ammo: 30,
      reloadUntil: 0,
      respawnAt: 0,
      protectedUntil: this.time + 1.2,
      shot: 0,
    })
    a.path = []
    a.nextRoute = 0
    a.input = idleInput()
    a.input.yaw = s.yaw
    a.queue = []
    a.nextShot = this.time + 0.3
  }
  save(a: Actor, completed: boolean) {
    if (a.p.bot) return
    const stats = {
      ...a.earned,
      matches: completed && this.time - a.joined > 10 ? 1 : 0,
    }
    if (Object.values(stats).some((n) => n > 0))
      this.onResult?.(a.p.id, stats)
    a.earned = { kills: 0, deaths: 0, wins: 0, matches: 0 }
  }
  tick() {
    this.time += DT
    if (this.restartAt) {
      if (this.time >= this.restartAt) {
        this.roundEnds = this.time + 240
        this.restartAt = 0
        this.roundId = crypto.randomUUID()
        for (const a of this.actors.values()) {
          a.p.kills = a.p.deaths = 0
          a.joined = this.time
          this.spawn(a)
        }
      }
      return
    }
    if (
      this.time >= this.roundEnds ||
      [...this.actors.values()].some((a) => a.p.kills >= 25)
    ) {
      const ranked = [...this.actors.values()].sort(
        (a, b) => b.p.kills - a.p.kills || a.p.deaths - b.p.deaths,
      )
      const winner = ranked[0]?.p.kills > 0 ? ranked[0] : null
      if (winner) winner.earned.wins++
      this.events.push({ type: 'round', winner: winner?.p.id ?? null })
      for (const a of this.actors.values()) this.save(a, true)
      this.restartAt = this.time + 9
      return
    }
    for (const a of this.actors.values()) {
      const p = a.p
      if (p.health <= 0) {
        a.queue = []
        if (this.time >= p.respawnAt) this.spawn(a)
        continue
      }
      if (p.bot) this.bot(a)
      else {
        const next = a.queue.shift()
        if (next) {
          a.input = next
          p.ack = next.seq
        } else {
          a.input = {
            ...a.input,
            forward: 0,
            side: 0,
            jump: false,
            fire: false,
            reload: false,
          }
        }
      }
      const i = a.input
      p.yaw = i.yaw
      p.pitch = i.pitch
      p.weapon = i.weapon
      if (p.weapon === 'knife') p.reloadUntil = 0
      move(p, i)
      if (p.reloadUntil && this.time >= p.reloadUntil) {
        p.ammo = 30
        p.reloadUntil = 0
      }
      if (p.weapon === 'rifle' && i.reload && p.ammo < 30 && !p.reloadUntil)
        p.reloadUntil = this.time + 1.65
      if (this.time - a.lastFire > 0.25) p.shot = 0
      if (i.fire && this.time >= a.nextShot && !p.reloadUntil) {
        if (p.weapon === 'rifle' && p.ammo === 0) {
          p.reloadUntil = this.time + 1.65
          continue
        }
        this.fire(a)
      }
    }
  }
  fire(a: Actor) {
    const p = a.p,
      knife = p.weapon === 'knife'
    a.nextShot = this.time + (knife ? 0.46 : 0.105)
    a.lastFire = this.time
    p.protectedUntil = 0
    if (!knife) p.ammo--
    const recoil = spray(p.shot, a.input.aim),
      dir = direction(
        p.yaw + (knife ? 0 : recoil.yaw),
        p.pitch + (knife ? 0 : recoil.pitch),
      )
    p.shot++
    const origin = { x: p.x, y: p.y + EYE, z: p.z }
    let distance = Math.min(knife ? 2.25 : 90, wallDistance(origin, dir)),
      victim: Actor | undefined,
      head = false
    for (const other of this.actors.values()) {
      const q = other.p
      if (q.id === p.id || q.health <= 0 || q.protectedUntil > this.time)
        continue
      const position = q
      const d = rayBox(
        origin,
        dir,
        { x: position.x - 0.32, y: position.y, z: position.z - 0.32 },
        { x: position.x + 0.32, y: position.y + HEIGHT, z: position.z + 0.32 },
      )
      if (d < distance) {
        distance = d
        victim = other
        head = origin.y + dir.y * d > position.y + 1.3
      }
    }
    const to = {
      x: origin.x + dir.x * distance,
      y: origin.y + dir.y * distance,
      z: origin.z + dir.z * distance,
    }
    this.events.push({
      type: 'shot',
      id: p.id,
      from: origin,
      to,
      weapon: p.weapon,
      hit: victim?.p.id,
      head,
    })
    if (victim) {
      const v = victim.p
      v.health = Math.max(0, v.health - (knife ? 65 : head ? 76 : 34))
      if (v.health === 0) {
        p.kills++
        a.earned.kills++
        v.deaths++
        victim.earned.deaths++
        v.respawnAt = this.time + 2.2
        v.vx = v.vy = v.vz = 0
        this.events.push({
          type: 'kill',
          killer: p.id,
          victim: v.id,
          weapon: p.weapon,
          head,
        })
      }
    }
  }
  bot(a: Actor) {
    const p = a.p,
      i = idleInput()
    i.seq = ++a.lastSeq
    i.yaw = p.yaw
    i.pitch = p.pitch
    const targets = [...this.actors.values()]
      .filter((b) => b !== a && b.p.health > 0)
      .map((b) => ({ b, d: Math.hypot(p.x - b.p.x, p.z - b.p.z) }))
      .sort((a, b) => a.d - b.d)
    const t = targets.find((t) => {
      const d = direction(Math.atan2(-(t.b.p.x - p.x), -(t.b.p.z - p.z)), 0)
      return wallDistance({ x: p.x, y: p.y + 1.3, z: p.z }, d) > t.d - 0.3
    })
    if (t && t.d < 30) {
      const q = t.b.p
      const aim = Math.atan2(-(q.x - p.x), -(q.z - p.z))
      let turn = ((aim - i.yaw + Math.PI * 3) % (Math.PI * 2)) - Math.PI
      i.yaw += Math.max(-0.07, Math.min(0.07, turn))
      i.pitch =
        Math.atan2(q.y - p.y - 0.18, t.d) +
        Math.sin(this.time * 3 + a.target) * 0.025
      i.fire = Math.abs(turn) < 0.08 && this.time - a.joined > 1 && Math.sin(this.time * 3 + a.target) > -0.35
      i.forward = t.d > 12 ? 1 : 0
      i.side =
        Math.sin(this.time * 0.6 + a.target) > 0.5
          ? 1
          : Math.sin(this.time * 0.6 + a.target) < -0.5
            ? -1
            : 0
    } else {
      if (this.time >= a.nextRoute || !a.path.length || a.stuck > 1) {
        const destination = targets[0]?.b.p ?? waypoints[a.target]
        a.path = route(p, destination)
        a.nextRoute = this.time + 1.5
        a.stuck = 0
      }
      while (a.path.length && Math.hypot(a.path[0].x - p.x, a.path[0].z - p.z) < 0.45) a.path.shift()
      const target = a.path[0]
      if (target) {
        i.yaw = Math.atan2(-(target.x - p.x), -(target.z - p.z))
        i.forward = 1
      }
      i.pitch = 0
    }

    if (Math.hypot(p.vx, p.vz) < 0.5) a.stuck += DT
    else a.stuck = 0
    if (p.ammo === 0) i.reload = true
    a.input = i
  }
  snapshot(): Snapshot {
    return {
      type: 'snapshot',
      time: this.time,
      roundEnds: this.roundEnds,
      restartAt: this.restartAt,
      roundId: this.roundId,
      players: [...this.actors.values()].map((a) => ({ ...a.p })),
      events: this.events.splice(0),
    }
  }
}

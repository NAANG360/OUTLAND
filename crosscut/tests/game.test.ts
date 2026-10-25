import { describe, expect, test } from 'bun:test'
import { body, DT, idleInput, move, validInput } from '../src/shared/physics'
import { Game } from '../src/shared/game'
import { direction, wallDistance } from '../src/shared/world'
const run = (
  p: ReturnType<typeof body>,
  input: ReturnType<typeof idleInput>,
  ticks: number,
) => {
  for (let n = 0; n < ticks; n++) move(p, input)
}
describe('movement', () => {
  test('gravity lands and standing remains stable', () => {
    const p = body(0, 5, 12)
    run(p, idleInput(), 180)
    expect(p.y).toBeCloseTo(0, 5)
    expect(p.grounded).toBe(true)
  })
  test('walls block movement at speed without tunneling', () => {
    const p = body(-22, 0, 12)
    const i = { ...idleInput(), side: -1, weapon: 'knife' as const }
    run(p, i, 600)
    expect(p.x).toBeGreaterThanOrEqual(-23.691)
    expect(p.x).toBeLessThan(-23)
  })
  test('jump lands, held jump chains and knife is faster', () => {
    const p = body(0, 0, 10)
    run(p, idleInput(), 2)
    run(p, { ...idleInput(), jump: true }, 20)
    expect(p.y).toBeGreaterThan(1)
    run(p, idleInput(), 100)
    expect(p.y).toBeCloseTo(0, 5)
    const rifle = body(0, 0, 10),
      knife = body(0, 0, 10)
    run(rifle, { ...idleInput(), side: 1 }, 35)
    run(knife, { ...idleInput(), side: 1, weapon: 'knife' }, 35)
    expect(knife.x).toBeGreaterThan(rifle.x)
    run(p, { ...idleInput(), jump: true }, 55)
    expect(p.y).toBeGreaterThan(0)
  })
  test('diagonal input cannot exceed cardinal ground speed', () => {
    const a = body(0, 0, 12),
      b = body(0, 0, 12)
    run(a, { ...idleInput(), forward: 1 }, 20)
    run(b, { ...idleInput(), forward: 1, side: 1 }, 20)
    expect(Math.hypot(b.vx, b.vz)).toBeCloseTo(Math.hypot(a.vx, a.vz), 4)
  })
  test('malformed and time-cheating inputs are rejected', () => {
    expect(validInput({ ...idleInput(), seq: 1 })).toBe(true)
    for (const patch of [
      { yaw: NaN },
      { forward: 3 },
      { pitch: Infinity },
      { seq: -1 },
      { jump: 1 },
      { weapon: 'rocket' },
    ])
      expect(validInput({ ...idleInput(), seq: 1, ...patch })).toBe(false)
  })
})
describe('combat', () => {
  function setup() {
    const g = new Game()
    g.add('a', 'A')
    g.add('b', 'B')
    const a = g.actors.get('a')!,
      b = g.actors.get('b')!
    Object.assign(a.p, body(0, 0, 12), {
      yaw: 0,
      pitch: -0.04,
      protectedUntil: 0,
    })
    Object.assign(b.p, body(0, 0, 7), { protectedUntil: 0 })
    a.input = { ...idleInput(), pitch: -0.04 }
    return { g, a, b }
  }
  test('rifle damages, kills and respawns in the simulation', () => {
    const { g, a, b } = setup()
    for (let n = 0; n < 3; n++) {
      g.time += 0.11
      g.fire(a)
    }
    expect(b.p.health).toBe(0)
    expect(a.p.kills).toBe(1)
    expect(b.p.deaths).toBe(1)
    for (let n = 0; n < 150; n++) g.tick()
    expect(b.p.health).toBe(100)
    expect(b.p.protectedUntil).toBeGreaterThan(g.time)
  })
  test('walls absorb bullets', () => {
    const { g, a, b } = setup()
    Object.assign(a.p, body(-1, 0, 6), { yaw: 0, pitch: 0 })
    Object.assign(b.p, body(-1, 0, -6))
    g.fire(a)
    expect(b.p.health).toBe(100)
    expect(wallDistance({ x: -1, y: 1.5, z: 6 }, direction(0, 0))).toBeLessThan(
      10,
    )
  })
  test('knife cannot hit outside its reach', () => {
    const { g, a, b } = setup()
    a.p.weapon = 'knife'
    g.fire(a)
    expect(b.p.health).toBe(100)
    b.p.z = 10.5
    g.fire(a)
    expect(b.p.health).toBe(35)
  })
  test('fire rate and ammo enforced through inputs', () => {
    const { g, a } = setup()
    for (let n = 1; n <= 120; n++) {
      g.enqueue('a', [{ ...idleInput(), seq: n, fire: true }])
      g.tick()
    }
    expect(a.p.ammo).toBeGreaterThan(10)
    expect(a.p.ammo).toBeLessThan(18)
  })
  test('input flooding cannot speed up simulation', () => {
    const g = new Game()
    g.add('a', 'A')
    const a = g.actors.get('a')!
    Object.assign(a.p, body(0, 0, 12))
    g.enqueue(
      'a',
      Array.from({ length: 100 }, (_, n) => ({
        ...idleInput(),
        seq: n + 1,
        side: 1,
      })),
    )
    g.tick()
    expect(a.p.ack).toBe(1)
    expect(a.queue.length).toBeLessThanOrEqual(11)
    expect(a.p.x).toBeLessThan(0.1)
  })
  test('25 kills ends the round and stats are saved once', () => {
    const { g, a } = setup()
    a.p.kills = 25
    a.earned.kills = 25
    a.joined = -20
    const receipts: any[] = []
    g.onResult = (...r) => receipts.push(r)
    g.tick()
    expect(g.restartAt).toBeGreaterThan(g.time)
    expect(receipts).toHaveLength(1)
    expect(receipts[0][1]).toEqual({
      kills: 25,
      deaths: 0,
      wins: 1,
      matches: 1,
    })
    for (let n = 0; n < 30; n++) g.tick()
    expect(receipts).toHaveLength(1)
  })
  test('spawn protection rejects damage', () => {
    const { g, a, b } = setup()
    b.p.protectedUntil = 10
    g.fire(a)
    expect(b.p.health).toBe(100)
  })
})

import { solids, type Vec3 } from './world'
export const DT = 1 / 60,
  HEIGHT = 1.72,
  EYE = 1.53,
  RADIUS = 0.31
export type Weapon = 'rifle' | 'knife'
export type Input = {
  seq: number
  yaw: number
  pitch: number
  forward: number
  side: number
  jump: boolean
  fire: boolean
  reload: boolean
  aim: boolean
  weapon: Weapon
}
export type Body = Vec3 & {
  vx: number
  vy: number
  vz: number
  grounded: boolean
}
export const idleInput = (): Input => ({
  seq: 0,
  yaw: 0,
  pitch: 0,
  forward: 0,
  side: 0,
  jump: false,
  fire: false,
  reload: false,
  aim: false,
  weapon: 'rifle',
})
export const body = (x = 0, y = 0, z = 0): Body => ({
  x,
  y,
  z,
  vx: 0,
  vy: 0,
  vz: 0,
  grounded: false,
})
export function move(p: Body, i: Input, dt = DT) {
  let wx = -Math.sin(i.yaw) * i.forward + Math.cos(i.yaw) * i.side,
    wz = -Math.cos(i.yaw) * i.forward - Math.sin(i.yaw) * i.side
  const len = Math.hypot(wx, wz)
  if (len > 0) {
    wx /= len
    wz /= len
  }
  const speed = i.weapon === 'knife' ? 9.3 : i.aim ? 4.4 : 7.2
  // Jump before friction preserves momentum and rewards chained landing jumps.
  if (p.grounded && i.jump) {
    p.vy = 8.2
    p.grounded = false
  }
  if (p.grounded) {
    const v = Math.hypot(p.vx, p.vz),
      drop = Math.max(v, 3) * 9 * dt,
      scale = v > 0 ? Math.max(0, v - drop) / v : 0
    p.vx *= scale
    p.vz *= scale
  }
  if (len > 0) {
    const projection = p.vx * wx + p.vz * wz
    const wish = p.grounded ? speed : Math.min(speed, 2.1)
    const add = Math.min(
      Math.max(0, wish - projection),
      (p.grounded ? speed * 14 : 24) * dt,
    )
    p.vx += add * wx
    p.vz += add * wz
  }
  const max = i.weapon === 'knife' ? 15 : 12.3,
    hs = Math.hypot(p.vx, p.vz)
  if (hs > max) {
    p.vx *= max / hs
    p.vz *= max / hs
  }
  p.vy = Math.max(-35, p.vy - 23 * dt)
  const steps = Math.max(
    1,
    Math.ceil(
      (Math.max(Math.abs(p.vx), Math.abs(p.vy), Math.abs(p.vz)) * dt) / 0.18,
    ),
  )
  p.grounded = false
  for (let n = 0; n < steps; n++)
    for (const axis of ['x', 'z', 'y'] as const) {
      const velocity = axis === 'x' ? 'vx' : axis === 'y' ? 'vy' : 'vz',
        delta = (p[velocity] * dt) / steps
      p[axis] += delta
      for (const b of solids) {
        const min = { x: b.x - b.w / 2, y: b.y - b.h / 2, z: b.z - b.d / 2 },
          max = { x: b.x + b.w / 2, y: b.y + b.h / 2, z: b.z + b.d / 2 }
        if (
          p.x + RADIUS <= min.x + 1e-6 ||
          p.x - RADIUS >= max.x - 1e-6 ||
          p.z + RADIUS <= min.z + 1e-6 ||
          p.z - RADIUS >= max.z - 1e-6 ||
          p.y + HEIGHT <= min.y + 1e-6 ||
          p.y >= max.y - 1e-6
        )
          continue
        if (axis === 'y') {
          p.y = delta > 0 ? min.y - HEIGHT : max.y
          if (delta <= 0) p.grounded = true
        } else p[axis] = delta > 0 ? min[axis] - RADIUS : max[axis] + RADIUS
        p[velocity] = 0
      }
    }
  if (p.y < -10) {
    p.y = 0
    p.vy = 0
  }
}
export function validInput(v: unknown): v is Input {
  if (!v || typeof v !== 'object') return false
  const i = v as Input
  return (
    Number.isSafeInteger(i.seq) &&
    i.seq > 0 &&
    i.seq < 2 ** 31 &&
    Number.isFinite(i.yaw) &&
    Math.abs(i.yaw) < 1e6 &&
    Number.isFinite(i.pitch) &&
    Math.abs(i.pitch) <= 1.5 &&
    [-1, 0, 1].includes(i.forward) &&
    [-1, 0, 1].includes(i.side) &&
    ['jump', 'fire', 'reload', 'aim'].every(
      (k) => typeof (i as any)[k] === 'boolean',
    ) &&
    (i.weapon === 'rifle' || i.weapon === 'knife')
  )
}
export function spray(shot: number, aim: boolean) {
  return {
    pitch: Math.min(shot, 10) * 0.0045 * (aim ? 0.7 : 1),
    yaw: Math.sin(shot * 1.7) * Math.min(shot, 9) * 0.0019 * (aim ? 0.7 : 1),
  }
}

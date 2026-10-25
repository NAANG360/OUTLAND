import { solids, type Vec3 } from './world'
// A compact ground navigation grid keeps opponents out of cover and door frames.
const size = 1.5, width = 31, depth = 29
const point = (id: number): Vec3 => ({ x: (id % width) * size - 22.5, y: 0, z: Math.floor(id / width) * size - 21 })
const clear = (p: Vec3) => !solids.some(b => b.y + b.h / 2 > 0.05 && b.y - b.h / 2 < 1.8 && Math.abs(p.x - b.x) < b.w / 2 + 0.45 && Math.abs(p.z - b.z) < b.d / 2 + 0.45)
const walkable = Array.from({ length: width * depth }, (_, i) => clear(point(i)))
const neighbors = walkable.map((ok, id) => ok ? [-width, -1, 1, width].map(d => id + d).filter(n => walkable[n] && Math.abs(n % width - id % width) <= 1) : [])
function nearest(p: Vec3) {
  let best = -1, distance = Infinity
  for (let id = 0; id < walkable.length; id++) {
    if (!walkable[id]) continue
    const q = point(id), d = (q.x - p.x) ** 2 + (q.z - p.z) ** 2
    if (d < distance) { distance = d; best = id }
  }
  return best
}
export function route(from: Vec3, to: Vec3): Vec3[] {
  const start = nearest(from), goal = nearest(to)
  if (start < 0 || goal < 0) return []
  const queue = [start], parent = new Map<number, number>([[start, -1]])
  for (let cursor = 0; cursor < queue.length; cursor++) {
    const id = queue[cursor]
    if (id === goal) {
      const path: Vec3[] = []
      for (let at = id; at !== start; at = parent.get(at)!) path.push(point(at))
      return path.reverse()
    }
    for (const n of neighbors[id]) if (!parent.has(n)) { parent.set(n, id); queue.push(n) }
  }
  return []
}

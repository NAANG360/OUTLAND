import * as T from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { boxes, type Vec3 } from '../shared/world'
import { EYE, spray, type Weapon } from '../shared/physics'
import type { GameEvent, Player } from '../shared/protocol'
const colors = {
  stone: 0xc5ab88,
  teal: 0x416d6a,
  wood: 0x957650,
  dark: 0x26383b,
  trim: 0xead2a8,
}
export class World {
  renderer: T.WebGLRenderer
  scene = new T.Scene()
  camera = new T.PerspectiveCamera(85, 1, 0.06, 180)
  viewScene = new T.Scene()
  viewCamera = new T.PerspectiveCamera(63, 1, 0.02, 10)
  rifle = new T.Group()
  knife = new T.Group()
  muzzle: T.Mesh
  players = new Map<
    string,
    { root: T.Group; legs: T.Group[]; arms: T.Group[]; shadow: T.Mesh }
  >()
  effects: { mesh: T.Object3D; life: number; velocity?: T.Vector3 }[] = []
  kick = 0
  slash = 0
  reload = 0
  bob = 0
  landing = 0
  aim = 0
  lastY = 0
  wasGrounded = false
  fov = 85
  time = 0
  menu = true
  constructor(public canvas: HTMLCanvasElement) {
    this.renderer = new T.WebGLRenderer({
      canvas,
      antialias: true,
      powerPreference: 'high-performance',
    })
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, 1.75))
    this.renderer.outputColorSpace = T.SRGBColorSpace
    this.renderer.toneMapping = T.ACESFilmicToneMapping
    this.renderer.toneMappingExposure = 1.22
    this.renderer.shadowMap.enabled = true
    this.renderer.shadowMap.type = T.PCFShadowMap
    this.scene.background = new T.Color(0xb5ccce)
    this.scene.fog = new T.Fog(0xb5ccce, 55, 130)
    this.scene.add(new T.HemisphereLight(0xdceefd, 0x9b7958, 2.8))
    const sun = new T.DirectionalLight(0xffe3af, 3.3)
    sun.position.set(-28, 46, 23)
    sun.castShadow = true
    sun.shadow.mapSize.set(2048, 2048)
    Object.assign(sun.shadow.camera, {
      left: -40,
      right: 40,
      top: 40,
      bottom: -40,
      near: 1,
      far: 120,
    })
    sun.shadow.bias = -0.0004
    sun.shadow.normalBias = 0.035
    this.scene.add(sun)
    this.buildMap()
    this.viewScene.add(new T.HemisphereLight(0xf0f5ff, 0x8d6955, 3))
    const light = new T.DirectionalLight(0xffe4bf, 4)
    light.position.set(-2, 4, 1)
    this.viewScene.add(light)
    this.rifle = this.makeRifle()
    this.knife = this.makeKnife()
    this.viewScene.add(this.rifle, this.knife)
    this.muzzle = new T.Mesh(
      new T.ConeGeometry(0.055, 0.16, 5),
      new T.MeshBasicMaterial({
        color: 0xffe9a2,
        transparent: true,
        opacity: 0.9,
      }),
    )
    this.muzzle.rotation.x = -Math.PI / 2
    this.muzzle.position.set(0, 0.045, -0.75)
    this.rifle.add(this.muzzle)
    this.muzzle.visible = false
    this.resize()
    window.addEventListener('resize', () => this.resize())
  }
  resize() {
    const w = innerWidth,
      h = innerHeight
    this.renderer.setSize(w, h, false)
    this.camera.aspect = w / h
    this.camera.updateProjectionMatrix()
    this.viewCamera.aspect = w / h
    this.viewCamera.updateProjectionMatrix()
  }
  buildMap() {
    const batches = new Map<number, T.BufferGeometry[]>()
    const add = (
      x: number,
      y: number,
      z: number,
      w: number,
      h: number,
      d: number,
      color: number,
      ry = 0,
    ) => {
      const g = new T.BoxGeometry(w, h, d)
      g.rotateY(ry)
      g.translate(x, y, z)
      if (!batches.has(color)) batches.set(color, [])
      batches.get(color)!.push(g)
    }
    for (const b of boxes) {
      add(b.x, b.y, b.z, b.w, b.h, b.d, colors[b.material])
      if (b.material === 'stone' && b.h > 2) {
        add(
          b.x,
          b.y + b.h / 2 + 0.08,
          b.z,
          b.w + 0.15,
          0.16,
          b.d + 0.15,
          0xdfc6a1,
        )
      }
    }
    // Fine masonry lines, grouped into a handful of draw calls.
    for (let z = -21; z < 23; z += 2)
      for (const x of [-23.98, 23.98]) {
        add(x, 1.6, z, 0.015, 0.025, 1.96, 0x9e8e77)
        add(x, 3.2, z, 0.015, 0.025, 1.96, 0x9e8e77)
        add(x, 2.4, z, 0.018, 1.55, 0.025, 0xab9679)
      }
    for (let z = -20; z < 23; z += 4)
      for (let x = -22; x < 24; x += 4) {
        add(x, 0.006, z, 3.98, 0.008, 0.025, 0xbca487)
        add(x, 0.008, z, 0.025, 0.008, 3.98, 0xbca487)
      }
    // Teal shutters and inset workshop windows.
    for (const [x, z] of [
      [-19.5, -7.47],
      [-10.4, -7.47],
      [11, 10.53],
      [19, 10.53],
    ]) {
      add(x, 2.1, z, 2.7, 3.8, 0.1, 0x304b4d)
      for (let n = 0; n < 9; n++)
        add(x, 0.5 + n * 0.4, z + 0.08, 2.6, 0.3, 0.12, 0x547c76)
      add(x, 4.2, z, 3.2, 0.22, 0.45, 0xead2a8)
    }
    for (const x of [-21, -18, -12, -9]) {
      add(x, 3, -5.4, 0.22, 6, 0.22, 0x77654e)
      add(x, 5.9, -4.8, 0.3, 0.3, 6, 0x77654e)
    }
    for (let x = -21.5; x < -8; x += 0.65)
      add(x, 6, -5.2, 0.22, 0.18, 5.6, 0xb9a581)
    // Container corrugation and corner hardware.
    for (let z = 5; z <= 11; z += 0.35)
      add(-8.38, 1.4, z, 0.08, 2.7, 0.1, 0x557f78)
    for (let x = 10.5; x <= 15.5; x += 0.35)
      add(x, 1.3, -9.46, 0.1, 2.5, 0.08, 0x557f78)
    for (const z of [4.85, 11.15])
      for (const x of [-11.55, -8.45]) add(x, 1.45, z, 0.1, 2.9, 0.1, 0x263f3d)
    // Crate battens and dark metal straps.
    for (const b of boxes.filter((b) => b.material === 'wood')) {
      for (let h = -b.h / 2 + 0.1; h < b.h / 2; h += 0.3)
        add(b.x, b.y + h, b.z + b.d / 2 + 0.01, b.w, 0.018, 0.025, 0x71583f)
      for (const x of [-0.35, 0.35])
        add(b.x + x * b.w, b.y, b.z + b.d / 2 + 0.03, 0.1, b.h, 0.05, 0x3b4744)
    }
    // Roof skylights, drainage, painted lane marks and low stone kerbs.
    for (const x of [11, 14, 17, 20]) add(x, 7.68, 5, 1.6, 0.1, 7.3, 0x678785)
    for (let z = -21; z < 22; z += 3) {
      add(6.5, 0.015, z, 0.13, 0.015, 1.3, 0xe7d7b8)
    }
    for (let z = -18; z < 22; z += 1.1) {
      add(-22.7, 0.08, z, 0.5, 0.16, 0.9, 0xd2bd9c)
    }
    add(3.4, 6.8, -2.74, 0.65, 0.7, 0.06, 0x386460)
    add(3.4, 6.8, -2.78, 0.08, 0.45, 0.02, 0xe3ceaa)
    // Distant quarry terraces surround the arena instead of an empty horizon.
    for (let i = 0; i < 28; i++) {
      const angle = (i / 28) * Math.PI * 2,
        r = 51 + Math.sin(i * 5) * 7,
        h = 9 + Math.sin(i * 3.2) * 5
      add(
        Math.sin(angle) * r,
        h / 2 - 1,
        Math.cos(angle) * r,
        14,
        h,
        13,
        i % 3 === 0 ? 0x9ca59b : 0xbaac95,
        angle * 0.6,
      )
    }
    add(0, -1.5, 0, 180, 1, 180, 0xbaaa8e)
    // Sparse desert plants, steel lamps, ochre cables.
    for (const [x, z] of [
      [-22, 19],
      [22, -19],
      [-6, -20],
      [21, 20],
    ]) {
      add(x, 0.4, z, 1.2, 0.8, 1.2, 0x9d7355)
      for (let n = 0; n < 5; n++)
        add(
          x + Math.sin(n) * 0.22,
          1.2 + Math.cos(n) * 0.1,
          z + Math.cos(n) * 0.22,
          0.2,
          1.5,
          0.2,
          0x677b58,
          n,
        )
    }
    for (const [color, geoms] of batches) {
      const merged = mergeGeometries(geoms)
      const mesh = new T.Mesh(
        merged,
        new T.MeshStandardMaterial({
          color,
          roughness: 0.91,
          flatShading: true,
        }),
      )
      mesh.castShadow = true
      mesh.receiveShadow = true
      this.scene.add(mesh)
      for (const g of geoms) g.dispose()
    }
    const wire = new T.Line(
      new T.BufferGeometry().setFromPoints(
        Array.from(
          { length: 30 },
          (_, i) =>
            new T.Vector3(
              -21 + i * 1.5,
              8 - Math.sin((i / 29) * Math.PI) * 2,
              -5,
            ),
        ),
      ),
      new T.LineBasicMaterial({ color: 0x474944 }),
    )
    this.scene.add(wire)
    // Three angular fabric shades add a distinct silhouette.
    const sail = new T.BufferGeometry()
    sail.setAttribute(
      'position',
      new T.Float32BufferAttribute(
        [-21, 6.2, -7, -8, 6.2, -7, -19, 6.1, -2],
        3,
      ),
    )
    sail.computeVertexNormals()
    const fabric = new T.Mesh(
      sail,
      new T.MeshStandardMaterial({
        color: 0xd1af78,
        side: T.DoubleSide,
        roughness: 1,
      }),
    )
    fabric.castShadow = true
    this.scene.add(fabric)
  }
  part(
    group: T.Group,
    x: number,
    y: number,
    z: number,
    w: number,
    h: number,
    d: number,
    color: number,
    rx = 0,
  ) {
    const mesh = new T.Mesh(
      new T.BoxGeometry(w, h, d),
      new T.MeshStandardMaterial({
        color,
        roughness: 0.64,
        metalness: color === 0x283236 ? 0.5 : 0,
        flatShading: true,
      }),
    )
    mesh.position.set(x, y, z)
    mesh.rotation.x = rx
    group.add(mesh)
    return mesh
  }
  makeRifle() {
    const g = new T.Group(),
      p = this.part.bind(this, g)
    p(0, 0, 0, 0.095, 0.12, 0.38, 0x283236)
    p(0, 0.065, -0.04, 0.073, 0.035, 0.28, 0x39474a)
    p(0, -0.005, -0.32, 0.086, 0.09, 0.25, 0x936649)
    p(0, 0.045, -0.37, 0.039, 0.035, 0.33, 0x283236)
    p(0, 0.027, -0.59, 0.031, 0.04, 0.3, 0x283236)
    p(0, 0.09, -0.65, 0.025, 0.1, 0.033, 0x283236)
    p(0, 0.145, -0.66, 0.055, 0.02, 0.025, 0x283236)
    p(0, -0.12, 0.07, 0.064, 0.18, 0.075, 0x675844, -0.22)
    p(0, -0.15, -0.115, 0.07, 0.24, 0.1, 0x283236, -0.2)
    p(0, -0.29, -0.08, 0.065, 0.09, 0.09, 0x344144, -0.4)
    p(0, -0.027, 0.3, 0.09, 0.14, 0.25, 0x936649)
    p(0, -0.027, 0.44, 0.1, 0.15, 0.03, 0x263236)
    for (let n = 0; n < 5; n++) {
      p(0.046, -0.01, -0.25 - n * 0.032, 0.009, 0.07, 0.015, 0x6d4b36)
      p(
        0.038,
        -0.15 - n * 0.025,
        -0.13 + n * 0.005,
        0.007,
        0.008,
        0.06,
        0x465154,
      )
    }
    p(0.043, 0.027, 0.05, 0.05, 0.018, 0.055, 0x59615d)
    p(0, 0.095, 0.08, 0.06, 0.045, 0.05, 0x283236)
    // Gloved hands and forearms.
    p(0.025, -0.16, 0.11, 0.105, 0.1, 0.15, 0x333f3c, 0.15)
    p(0.035, -0.245, 0.24, 0.105, 0.12, 0.26, 0xb79b7a, -0.4)
    p(-0.06, -0.08, -0.3, 0.105, 0.1, 0.16, 0x34443f)
    p(-0.16, -0.19, -0.18, 0.11, 0.13, 0.35, 0xb79b7a, -0.5)
    return g
  }
  makeKnife() {
    const g = new T.Group(),
      p = this.part.bind(this, g)
    p(0, -0.08, 0, 0.07, 0.07, 0.2, 0x34423f)
    p(0, -0.08, -0.12, 0.15, 0.035, 0.035, 0x707f7e)
    p(0, -0.06, -0.27, 0.035, 0.055, 0.27, 0xb5c5bc)
    p(0, -0.04, -0.44, 0.025, 0.028, 0.09, 0xdce4d8, -0.12)
    p(0.015, -0.12, 0.035, 0.12, 0.1, 0.15, 0x374740)
    p(0.02, -0.23, 0.19, 0.12, 0.15, 0.26, 0xb79b7a, -0.5)
    return g
  }
  actor(p: Player) {
    const root = new T.Group(),
      part = this.part.bind(this, root)
    part(0, 1.04, 0, 0.58, 0.52, 0.32, 0x3f6563)
    part(0, 1.05, -0.18, 0.46, 0.34, 0.075, 0x263c3c)
    for (const x of [-0.15, 0, 0.15])
      part(x, 1.03, -0.24, 0.12, 0.19, 0.08, 0x607f71)
    part(0, 1.47, 0, 0.36, 0.35, 0.33, 0xb79b7a)
    part(0, 1.66, 0, 0.43, 0.14, 0.4, 0x3a5350)
    part(0, 1.52, -0.18, 0.34, 0.09, 0.04, 0x263536)
    part(0, 1.58, -0.23, 0.45, 0.04, 0.09, 0x3a5350)
    part(0.3, 1.17, 0, 0.08, 0.15, 0.2, 0xd6a364)
    part(-0.3, 1.17, 0, 0.08, 0.15, 0.2, 0xd6a364)
    const legs: T.Group[] = [],
      arms: T.Group[] = []
    for (const side of [-1, 1]) {
      const leg = new T.Group()
      leg.position.set(side * 0.16, 0.78, 0)
      this.part(leg, 0, -0.31, 0, 0.23, 0.58, 0.25, 0x535c50)
      this.part(leg, 0, -0.69, -0.055, 0.25, 0.19, 0.35, 0x2c3834)
      root.add(leg)
      legs.push(leg)
      const arm = new T.Group()
      arm.position.set(side * 0.37, 1.2, 0)
      this.part(arm, 0, -0.21, -0.08, 0.2, 0.43, 0.22, 0x4c6b5c, -0.4)
      this.part(arm, 0, -0.37, -0.18, 0.18, 0.15, 0.18, 0x283c36)
      root.add(arm)
      arms.push(arm)
    }
    part(0.22, 0.98, -0.45, 0.07, 0.08, 0.6, 0x283236)
    part(0.22, 0.99, -0.43, 0.085, 0.08, 0.23, 0x9e7050)
    const shadow = new T.Mesh(
      new T.CircleGeometry(0.52, 20),
      new T.MeshBasicMaterial({
        color: 0x343730,
        transparent: true,
        opacity: 0.18,
        depthWrite: false,
      }),
    )
    shadow.rotation.x = -Math.PI / 2
    this.scene.add(shadow, root)
    this.players.set(p.id, { root, legs, arms, shadow })
    return this.players.get(p.id)!
  }
  updatePlayers(players: Player[], localId: string) {
    const seen = new Set<string>()
    for (const p of players) {
      if (p.id === localId) continue
      seen.add(p.id)
      const a = this.players.get(p.id) ?? this.actor(p)
      a.root.visible = p.health > 0
      a.shadow.visible = p.health > 0
      a.root.position.set(p.x, p.y, p.z)
      a.root.rotation.y = p.yaw
      a.shadow.position.set(p.x, 0.018, p.z)
      a.shadow.scale.setScalar(1 + Math.max(0, p.y) * 0.1)
      const walk = Math.min(1, Math.hypot(p.vx, p.vz) / 6)
      a.legs.forEach(
        (leg, n) =>
          (leg.rotation.x =
            Math.sin(this.time * 13 + n * Math.PI) * 0.65 * walk),
      )
      a.arms.forEach((arm) => (arm.rotation.x = -p.pitch))
    }
    for (const [id, a] of this.players)
      if (!seen.has(id)) {
        this.scene.remove(a.root, a.shadow)
        a.root.traverse((o) => {
          if (o instanceof T.Mesh) {
            o.geometry.dispose()
            ;(o.material as T.Material).dispose()
          }
        })
        a.shadow.geometry.dispose()
        ;(a.shadow.material as T.Material).dispose()
        this.players.delete(id)
      }
  }
  shot(weapon: Weapon) {
    if (weapon === 'rifle') {
      this.kick = Math.min(0.12, this.kick + 0.065)
      this.muzzle.visible = true
      setTimeout(() => (this.muzzle.visible = false), 35)
    } else this.slash = 1
  }
  event(e: GameEvent, localId: string) {
    if (e.type !== 'shot') return
    if (e.id !== localId && e.weapon === 'rifle') {
      const points = [
        new T.Vector3(e.from.x, e.from.y - 0.2, e.from.z),
        new T.Vector3(e.to.x, e.to.y, e.to.z),
      ]
      const line = new T.Line(
        new T.BufferGeometry().setFromPoints(points),
        new T.LineBasicMaterial({
          color: 0xffe2aa,
          transparent: true,
          opacity: 0.65,
        }),
      )
      this.scene.add(line)
      this.effects.push({ mesh: line, life: 0.06 })
    }
    if (e.weapon === 'rifle') {
      for (let n = 0; n < (e.hit ? 5 : 3); n++) {
        const mesh = new T.Mesh(
          new T.BoxGeometry(0.035, 0.035, 0.035),
          new T.MeshBasicMaterial({ color: e.hit ? 0xc99166 : 0xf0dab7 }),
        )
        mesh.position.set(e.to.x, e.to.y, e.to.z)
        this.scene.add(mesh)
        this.effects.push({
          mesh,
          life: 0.25 + Math.random() * 0.15,
          velocity: new T.Vector3(
            (Math.random() - 0.5) * 3,
            Math.random() * 3,
            (Math.random() - 0.5) * 3,
          ),
        })
      }
    }
  }
  render(
    dt: number,
    local?: Player,
    look?: { yaw: number; pitch: number; aim: boolean },
    idle = false,
  ) {
    this.time += dt
    for (let n = this.effects.length - 1; n >= 0; n--) {
      const e = this.effects[n]
      e.life -= dt
      if (e.velocity) {
        e.velocity.y -= 10 * dt
        e.mesh.position.addScaledVector(e.velocity, dt)
      }
      if (e.life <= 0) {
        this.scene.remove(e.mesh)
        const m = e.mesh as T.Mesh
        m.geometry.dispose()
        ;(m.material as T.Material).dispose()
        this.effects.splice(n, 1)
      }
    }
    this.kick *= Math.exp(-dt * 19)
    this.slash *= Math.exp(-dt * 10)
    if (!local) {
      const a = Math.sin(this.time * 0.045) * 0.08
      this.camera.position.set(27 + Math.sin(a) * 7, 20, 31)
      this.camera.lookAt(-3, 2, -2)
      this.camera.fov = 58
      this.camera.updateProjectionMatrix()
    } else if (look) {
      const speed = Math.hypot(local.vx, local.vz)
      this.bob += dt * speed * 1.5
      if (!this.wasGrounded && local.grounded) this.landing = 0.055
      this.wasGrounded = local.grounded
      this.landing *= Math.exp(-dt * 14)
      this.aim +=
        (Number(look.aim && local.weapon === 'rifle') - this.aim) *
        (1 - Math.exp(-dt * 15))
      this.camera.position.set(
        local.x,
        local.y +
          EYE -
          this.landing +
          (local.grounded ? Math.sin(this.bob * 2) * 0.012 : 0),
        local.z,
      )
      const recoil = spray(Math.max(0, local.shot - 1), look.aim)
      this.camera.rotation.order = 'YXZ'
      this.camera.rotation.set(
        look.pitch + this.kick * 0.24 + recoil.pitch,
        look.yaw + recoil.yaw,
        0,
      )
      this.camera.fov = this.fov - this.aim * 18 + Math.max(0, speed - 8) * 0.35
      this.camera.updateProjectionMatrix()
      this.rifle.visible = local.weapon === 'rifle' && local.health > 0
      this.knife.visible = local.weapon === 'knife' && local.health > 0
      const reload =
        local.reloadUntil > 0
          ? Math.sin(Math.max(0, Math.min(1, this.reload)) * Math.PI)
          : 0
      const g = local.weapon === 'rifle' ? this.rifle : this.knife
      g.position.set(
        0.24 * (1 - this.aim) + Math.cos(this.bob) * 0.009,
        -0.26 +
          this.aim * 0.12 -
          Math.abs(Math.sin(this.bob)) * 0.009 -
          reload * 0.22,
        -0.65 + this.kick + 0.08 * this.slash,
      )
      g.rotation.set(
        this.kick * 0.65 + reload * 0.65 - this.slash * 0.7,
        -0.035 + this.slash * 0.5,
        -0.03 - reload * 0.5 + this.slash * 0.7,
      )
    }
    this.renderer.autoClear = true
    this.renderer.render(this.scene, this.camera)
    this.renderer.shadowMap.autoUpdate = false
    if (local && look && !idle) {
      this.renderer.autoClear = false
      this.renderer.clearDepth()
      this.renderer.render(this.viewScene, this.viewCamera)
    }
  }
}

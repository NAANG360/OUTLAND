import '@fontsource-variable/space-grotesk'
import '@fontsource-variable/inter'
import './style.css'
import { World } from './scene'
import { Sound } from './audio'
import { DT, idleInput, type Input } from '../shared/physics'
import type { Player, Snapshot } from '../shared/protocol'
import { SoloSession, readStats } from '../shared/session'
const $ = <T extends HTMLElement = HTMLElement>(id: string) =>
  document.getElementById(id) as T
const icon = (name: string) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${({ arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>', plus: '<path d="M12 5v14M5 12h14"/>', close: '<path d="m6 6 12 12M6 18 18 6"/>', gear: '<circle cx="12" cy="12" r="3"/><path d="m10 3-1 3-3 1-3 3v4l3 1 1 3 3 3h4l1-3 3-1 3-3v-4l-3-1-1-3-3-3z"/>', sound: '<path d="M11 4 5 9H2v6h3l6 5zM15 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/>', target: '<circle cx="12" cy="12" r="7"/><path d="M12 1v5m0 12v5M1 12h5m12 0h5"/>', user: '<circle cx="12" cy="8" r="3.5"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/>', chevron: '<path d="m9 5 7 7-7 7"/>', link: '<path d="m10 13 4-4m-7 6-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0m4 2 1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0" transform="translate(2 0)"/>' } as Record<string, string>)[name] ?? ''}</svg>`
const logo = `<svg class="brand-mark" viewBox="0 0 36 32" aria-hidden="true"><path d="m2 3 9 13-9 13h8l9-13L10 3zm14 0 9 13-9 13h8l9-13-9-13z" fill="currentColor"/></svg>`
$('app').innerHTML = `
 <div id="menu" class="menu">
  <header>${logo}<span class="wordmark">Crosscut</span><div class="header-right"><button id="statsButton" class="quiet">Your record</button></div></header>
  <main class="entry"><div class="eyebrow"><span class="live-dot"></span> Solo deathmatch</div><h1>Make<br>your mark.</h1><div class="play-panel"><div class="name-field">${icon('user')}<input id="nickname" maxlength="20" aria-label="Player name" placeholder="Your name" autocomplete="off" spellcheck="false"></div><button id="play" class="primary">Play now ${icon('arrow')}</button></div></main>
  <div class="map-caption"><span class="map-line"></span><div><span>Quarry</span><small>First to 25</small></div><span class="map-number">01</span></div>
  <footer><button id="controls" class="quiet">${icon('target')} Controls</button><button id="settings" class="quiet">${icon('gear')} Settings</button></footer>
 </div>
 <div id="hud" hidden><div class="match-top"><span id="matchName">Quarry</span><div id="matchClock">4:00</div><span id="matchScore">0 / 25</span></div><div class="connection"><span id="fps">— fps</span></div><div id="feed"></div><div id="crosshair"><i></i><i></i><i></i><i></i></div><div id="hitmarker"></div><div id="damage"></div><div id="killConfirm"></div><div id="respawn" hidden></div><div class="health"><span class="health-icon">+</span><span id="health">100</span><div class="health-track"><i id="healthBar"></i></div></div><div class="weapon"><div class="weapon-title"><span id="weaponName">Ak-47</span><kbd id="weaponKey">1</kbd></div><div id="ammo">30 <small>/ 30</small></div><div id="reloadProgress"><i></i></div></div><div class="game-bottom"><span id="speed"></span><span><kbd>tab</kbd> Score <kbd>esc</kbd> Menu</span></div></div>
 <div id="pause" class="scrim" hidden><section class="dialog compact"><div class="eyebrow">Crosscut</div><h2>Paused.</h2><button id="resume" class="primary">Resume ${icon('arrow')}</button><button id="freeLook" class="secondary" hidden>Play without mouse capture</button><button id="pauseSettings" class="secondary">Settings</button><button id="leave" class="quiet">End match</button></section></div>
 <div id="scoreboard" class="scrim" hidden><section class="dialog score-dialog"><div class="dialog-heading"><div><div class="eyebrow" id="scoreLabel">Deathmatch</div><h2 id="scoreTitle">Quarry</h2></div><span id="roundCountdown"></span></div><div class="score-head"><span>Player</span><span>Kills</span><span>Deaths</span></div><div id="scoreRows"></div></section></div>
 <div id="modal" class="scrim" hidden><section class="dialog"><div class="dialog-heading"><h2 id="modalTitle"></h2><button id="modalClose" class="icon-button" aria-label="Close">${icon('close')}</button></div><div id="modalBody"></div></section></div>
 
 <div id="toast" role="status" hidden></div><div id="unsupported" hidden>Mouse & keyboard required</div>`
const esc = (s: string) =>
  s.replace(
    /[&<>"']/g,
    (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[
        c
      ]!,
  )
const canvas = $<HTMLCanvasElement>('world'),
  sound = new Sound()
let world: World
try {
  world = new World(canvas)
} catch {
  $('menu').hidden = true
  $('unsupported').textContent = 'WebGL is unavailable in this browser.'
  $('unsupported').hidden = false
  throw Error('WebGL unavailable')
}
const storage = {
  getItem(key: string) { try { return localStorage.getItem(key) } catch { return null } },
  setItem(key: string, value: string) { try { localStorage.setItem(key, value) } catch { toast('Browser storage is unavailable.') } },
}
let sensitivity = Number(storage.getItem('crosscut-sensitivity') ?? 1),
  volume = Number(storage.getItem('crosscut-volume') ?? 0.5)
world.fov = Number(storage.getItem('crosscut-fov') ?? 85)
sound.setVolume(volume)
$<HTMLInputElement>('nickname').value =
  storage.getItem('crosscut-name') ?? ''
const selfId = 'you'
let session: SoloSession | undefined,
  local: Player | undefined,
  latest: Snapshot | undefined,
  previous: Snapshot | undefined,
  yaw = 0, pitch = 0,
  weapon: 'rifle' | 'knife' = 'rifle',
  fire = false, aim = false, reload = false,
  keys = new Set<string>(),
  accumulator = 0, hudClock = 0,
  frameCount = 0, fpsTime = 0, fps = 0,
  tab = false, lastReload = 0, lastRound = '',
  freeLook = false, captureFailed = false
let errorTimer: ReturnType<typeof setTimeout> | undefined,
  killTimer: ReturnType<typeof setTimeout> | undefined
const active = () => !!session
const running = () => !!session && !session.paused

function toast(message: string) {
  $('toast').textContent = message
  $('toast').hidden = false
  if (errorTimer) clearTimeout(errorTimer)
  errorTimer = setTimeout(() => ($('toast').hidden = true), 4500)
}
function clearInput() {
  keys.clear()
  fire = aim = reload = tab = false
  accumulator = 0
}
function pause() {
  if (!session) return
  session.paused = true
  session.save()
  freeLook = false
  clearInput()
  $('pause').hidden = false
  $('scoreboard').hidden = true
  canvas.classList.remove('playing')
  if (document.pointerLockElement) document.exitPointerLock()
}
function resume(capture = true) {
  if (!session) return
  clearInput()
  sound.start()
  session.paused = false
  freeLook = !capture
  $('pause').hidden = true
  $('modal').hidden = true
  canvas.classList.add('playing')
}
function captureError() {
  if (!session) return
  captureFailed = true
  pause()
  $('freeLook').hidden = false
  toast('Mouse capture is unavailable here. You can still play in this window.')
}
function lock() {
  if (captureFailed) { resume(false); return }
  sound.start()
  try {
    const result = canvas.requestPointerLock()
    if (result) result.catch(captureError)
  } catch { captureError() }
}
function start() {
  const name = $<HTMLInputElement>('nickname').value.trim() || 'You'
  storage.setItem('crosscut-name', name)
  session = new SoloSession(name, storage)
  latest = session.game.snapshot()
  previous = latest
  local = latest.players.find(p => p.id === selfId)!
  yaw = local.yaw
  pitch = local.pitch
  weapon = 'rifle'
  lastRound = latest.roundId
  lastReload = 0
  world.reload = 0
  world.kick = 0
  $('feed').innerHTML = ''
  $('killConfirm').className = ''
  $('damage').className = ''
  $('hitmarker').className = ''
  $('respawn').hidden = true
  $('menu').hidden = true
  $('hud').hidden = false
  displayHud()
  lock()
}
function leave() {
  session?.finish()
  session = undefined
  local = undefined
  latest = previous = undefined
  freeLook = false
  clearInput()
  if (document.pointerLockElement) document.exitPointerLock()
  canvas.classList.remove('playing')
  $('menu').hidden = false
  for (const id of ['hud', 'pause', 'scoreboard', 'modal']) $(id).hidden = true
  world.updatePlayers([], '')
}
function receive(s: Snapshot) {
  previous = latest
  latest = s
  const p = s.players.find(p => p.id === selfId)!
  const respawned = local && local.health <= 0 && p.health > 0
  const round = lastRound !== s.roundId
  const wasAlive = local && local.health > 0
  if (respawned || round) {
    yaw = p.yaw
    pitch = p.pitch
    weapon = 'rifle'
    world.kick = 0
    previous = s
    clearInput()
  }
  lastRound = s.roundId
  if (local && p.health < local.health) {
    $('damage').classList.remove('flash')
    void $('damage').offsetWidth
    $('damage').classList.add('flash')
  }
  local = p
  if (p.reloadUntil && lastReload !== p.reloadUntil) {
    lastReload = p.reloadUntil
    sound.reload()
  }
  for (const e of s.events) {
    world.event(e, selfId)
    if (e.type === 'shot') {
      if (e.id === selfId) {
        world.shot(e.weapon)
        if (e.weapon === 'rifle') sound.shot()
        else sound.knife()
      }
      if (e.id !== selfId) {
        const d = local
          ? Math.hypot(e.from.x - local.x, e.from.z - local.z)
          : 100
        if (d < 30) sound.shot(true)
      }
      if (e.id === selfId && e.hit) {
        sound.hit()
        $('hitmarker').className = e.head ? 'head hit' : 'hit'
        setTimeout(() => ($('hitmarker').className = ''), 120)
      }
    }
    if (e.type === 'kill') {
      const killer = s.players.find((p) => p.id === e.killer)?.name ?? 'Player',
        victim = s.players.find((p) => p.id === e.victim)?.name ?? 'Player'
      const item = document.createElement('div')
      item.className = 'feed-item' + (e.killer === selfId ? ' yours' : '')
      item.innerHTML = `<span>${esc(killer)}</span><span class="feed-weapon">${e.weapon === 'rifle' ? '⌁' : '／'}</span><span>${esc(victim)}</span>`
      $('feed').prepend(item)
      while ($('feed').children.length > 4) $('feed').lastChild?.remove()
      setTimeout(() => item.remove(), 4500)
      if (e.killer === selfId) {
        sound.kill()
        $('killConfirm').textContent = (e.head ? 'Headshot · ' : '') + victim
        $('killConfirm').className = 'visible'
        if (killTimer) clearTimeout(killTimer)
        killTimer = setTimeout(() => ($('killConfirm').className = ''), 1700)
      }
    }
  }
  if (wasAlive && p.health === 0) {
    fire = aim = false
    keys.clear()
  }
  $('respawn').hidden = p.health > 0 || !!s.restartAt
  updateScore()
}
function modal(title: string, body: string) {
  pause()
  $('modalTitle').textContent = title
  $('modalBody').innerHTML = body
  $('modal').hidden = false
  $('modalClose').focus()
}
function settings() {
  modal(
    'Settings',
    `<label class="setting"><span>Sensitivity</span><output id="sensitivityValue">${sensitivity.toFixed(2)}</output><input id="sensitivityInput" type="range" min="0.3" max="2.5" step=".05" value="${sensitivity}"></label><label class="setting"><span>Field of view</span><output id="fovValue">${world.fov}°</output><input id="fovInput" type="range" min="70" max="105" step="1" value="${world.fov}"></label><label class="setting"><span>Volume</span><output id="volumeValue">${Math.round(volume * 100)}%</output><input id="volumeInput" type="range" min="0" max="1" step=".05" value="${volume}"></label>`,
  )
  $<HTMLInputElement>('sensitivityInput').oninput = (e) => {
    sensitivity = Number((e.target as HTMLInputElement).value)
    $('sensitivityValue').textContent = sensitivity.toFixed(2)
    storage.setItem('crosscut-sensitivity', String(sensitivity))
  }
  $<HTMLInputElement>('fovInput').oninput = (e) => {
    world.fov = Number((e.target as HTMLInputElement).value)
    $('fovValue').textContent = world.fov + '°'
    storage.setItem('crosscut-fov', String(world.fov))
  }
  $<HTMLInputElement>('volumeInput').oninput = (e) => {
    volume = Number((e.target as HTMLInputElement).value)
    sound.setVolume(volume)
    $('volumeValue').textContent = Math.round(volume * 100) + '%'
    storage.setItem('crosscut-volume', String(volume))
  }
}
$('settings').onclick = settings
$('pauseSettings').onclick = settings
$('controls').onclick = () =>
  modal(
    'Controls',
    `<dl class="controls-list">${[
      ['Move', 'w a s d'],
      ['Look', 'Mouse'],
      ['Fire', 'Left click'],
      ['Aim', 'Right click'],
      ['Jump / bunny hop', 'Hold space'],
      ['Reload', 'r'],
      ['Rifle / knife', '1 / 2'],
      ['Score', 'tab'],
      ['Menu', 'esc'],
    ]
      .map(([label, key]) => `<div><dt>${label}</dt><dd>${key}</dd></div>`)
      .join('')}</dl>`,
  )
$('modalClose').onclick = () => {
  $('modal').hidden = true
}
$('statsButton').onclick = () => {
  const s = readStats(storage)
  modal(
    'Your record',
    s
      ? `<div class="stats-grid">${[
          ['Wins', s.wins],
          ['Kills', s.kills],
          ['Deaths', s.deaths],
          ['Matches', s.matches],
        ]
          .map(
            ([label, value]) =>
              `<div><strong>${value}</strong><span>${label}</span></div>`,
          )
          .join('')}</div><p class="record-note">Saved in this browser</p>`
      : '<p>No matches yet.</p>',
  )
}
$('play').onclick = start
$('resume').onclick = lock
$('freeLook').onclick = () => resume(false)
$('leave').onclick = leave
window.addEventListener('keydown', (e) => {
  if ((e.target as HTMLElement).matches('input,textarea')) return
  if (active() && ['Space', 'Tab', 'ArrowUp', 'ArrowDown'].includes(e.code)) e.preventDefault()
  if (e.code === 'Escape') {
    $('modal').hidden = true
    pause()
    return
  }
  if (!running()) return
  if (e.code === 'Tab') { tab = true; updateScore() }
  keys.add(e.code)
  if (e.code === 'Digit1') weapon = 'rifle'
  if (e.code === 'Digit2') weapon = 'knife'
  if (e.code === 'KeyR') reload = true
})
window.addEventListener('keyup', (e) => {
  keys.delete(e.code)
  if (e.code === 'Tab') { tab = false; updateScore() }
})
window.addEventListener('blur', pause)
window.addEventListener('pagehide', () => session?.save())
document.addEventListener('visibilitychange', () => { if (document.hidden) pause() })
document.addEventListener('pointerlockchange', () => {
  if (document.pointerLockElement === canvas) resume()
  else if (running() && !freeLook) pause()
})
canvas.addEventListener('mousemove', (e) => {
  if (!running()) return
  yaw -= e.movementX * 0.002 * sensitivity
  pitch = Math.max(-1.48, Math.min(1.48, pitch - e.movementY * 0.002 * sensitivity))
  yaw = ((((yaw + Math.PI) % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2)) - Math.PI
})
canvas.addEventListener('mouseleave', () => { if (freeLook) { fire = aim = false } })
canvas.addEventListener('mousedown', (e) => {
  if (!running()) return
  if (e.button === 0) fire = true
  if (e.button === 2) aim = true
})
window.addEventListener('mouseup', (e) => {
  if (e.button === 0) fire = false
  if (e.button === 2) aim = false
})
canvas.addEventListener('contextmenu', (e) => e.preventDefault())
canvas.addEventListener('wheel', (e) => {
  if (running()) {
    e.preventDefault()
    weapon = weapon === 'rifle' ? 'knife' : 'rifle'
  }
}, { passive: false })
function updateScore() {
  const s = latest
  if (!s) return
  $('scoreboard').hidden = !running() || !(tab || s.restartAt)
  if ($('scoreboard').hidden) return
  const ranked = [...s.players].sort(
    (a, b) => b.kills - a.kills || a.deaths - b.deaths,
  )
  $('scoreLabel').textContent = s.restartAt ? 'Round complete' : 'Deathmatch'
  $('scoreTitle').textContent = s.restartAt
    ? ranked[0]?.kills
      ? ranked[0].id === selfId
        ? 'Your round.'
        : ranked[0].name + ' wins'
      : 'Good warm-up.'
    : 'Quarry'
  $('roundCountdown').textContent = s.restartAt
    ? 'Next round in ' + Math.max(0, Math.ceil(s.restartAt - s.time))
    : ''
  $('scoreRows').innerHTML = ranked
    .map(
      (p, n) =>
        `<div class="score-row ${p.id === selfId ? 'self' : ''}"><span><b>${n + 1}</b>${esc(p.name)}${p.bot ? '<small>Bot</small>' : ''}</span><span>${p.kills}</span><span>${p.deaths}</span></div>`,
    )
    .join('')
}
function displayHud() {
  if (!local || !latest) return
  const p = local,
    s = latest,
    remaining = Math.max(0, Math.ceil(s.roundEnds - s.time))
  $('matchClock').textContent =
    Math.floor(remaining / 60) + ':' + String(remaining % 60).padStart(2, '0')
  $('matchScore').textContent = p.kills + ' / 25'
  $('health').textContent = String(p.health)
  $('healthBar').style.width = p.health + '%'
  $('healthBar').className = p.health <= 34 ? 'low' : ''
  $('weaponName').textContent = weapon === 'rifle' ? 'Ak-47' : 'Knife'
  $('weaponKey').textContent = weapon === 'rifle' ? '1' : '2'
  $('ammo').innerHTML =
    weapon === 'rifle'
      ? p.ammo + ' <small>/ 30</small>'
      : '<span class="knife-glyph">⌁</span>'
  $('reloadProgress').className = p.reloadUntil ? 'active' : ''
  ;($('reloadProgress').firstElementChild as HTMLElement).style.width =
    (1 - Math.max(0, p.reloadUntil - s.time) / 1.65) * 100 + '%'
  world.reload = p.reloadUntil
    ? 1 - Math.max(0, p.reloadUntil - s.time) / 1.65
    : 0
  $('respawn').textContent =
    'Back in ' + Math.max(1, Math.ceil(p.respawnAt - s.time))
  $('speed').textContent = Math.hypot(p.vx, p.vz).toFixed(1) + ' m/s'
  $('fps').textContent = fps + ' fps'
  $('crosshair').className =
    (aim ? 'aim ' : '') + (p.health <= 0 || s.restartAt ? 'hidden' : '')
  $('crosshair').style.setProperty(
    '--gap',
    5 + Math.hypot(p.vx, p.vz) * 0.35 + world.kick * 90 + 'px',
  )
  if (s.restartAt) updateScore()
}
function interpolated(): Player[] {
  if (!latest) return []
  const alpha = accumulator / DT
  return latest.players.map(p => {
    const b = previous?.players.find(q => q.id === p.id)
    if (!b || p.health <= 0 || b.health <= 0 || Math.hypot(b.x - p.x, b.z - p.z) > 4) return p
    const turn = ((p.yaw - b.yaw + Math.PI * 3) % (Math.PI * 2)) - Math.PI
    return { ...p, x: b.x + (p.x - b.x) * alpha, y: b.y + (p.y - b.y) * alpha,
      z: b.z + (p.z - b.z) * alpha, yaw: b.yaw + turn * alpha }
  })
}
let last = performance.now()
function frame(now: number) {
  requestAnimationFrame(frame)
  const dt = Math.min((now - last) / 1000, 0.05)
  last = now
  frameCount++
  fpsTime += dt
  if (fpsTime >= 0.5) {
    fps = Math.round(frameCount / fpsTime)
    frameCount = 0
    fpsTime = 0
  }
  if (running() && session && local) {
    accumulator += dt
    hudClock += dt
    while (accumulator >= DT) {
      accumulator -= DT
      const alive = local.health > 0 && !latest?.restartAt
      const input: Input = { ...idleInput(), yaw, pitch, weapon,
        forward: alive ? Number(keys.has('KeyW')) - Number(keys.has('KeyS')) : 0,
        side: alive ? Number(keys.has('KeyD')) - Number(keys.has('KeyA')) : 0,
        jump: alive && keys.has('Space'), fire: alive && fire,
        reload: alive && reload, aim: alive && aim }
      reload = false
      const snapshot = session.step(input)
      if (snapshot) receive(snapshot)
    }
    if (hudClock >= 0.1) { hudClock = 0; displayHud() }
    if (local.grounded && Math.hypot(local.vx, local.vz) > 2 && local.health > 0)
      sound.step(session.game.time, Math.hypot(local.vx, local.vz))
  }
  const players = running() ? interpolated() : latest?.players ?? []
  world.updatePlayers(players, selfId)
  world.render(running() || !active() ? dt : 0, players.find(p => p.id === selfId), local ? { yaw, pitch, aim } : undefined, false)
}
requestAnimationFrame(frame)
if (matchMedia('(pointer: coarse)').matches) {
  $('unsupported').hidden = false
  $('play').setAttribute('disabled', '')
}

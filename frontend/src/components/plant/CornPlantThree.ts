import * as THREE from 'three'

export type PlantSpecimen = Record<string, any>

export type CornPlantView = {
  onSelect: ((specimen: PlantSpecimen) => void) | null
  setSpecimen: (specimen: PlantSpecimen) => void
  destroy: () => void
}

function clamp(v: number, a: number, b: number) {
  return Math.max(a, Math.min(b, v))
}
function n(v: unknown, d: number) {
  const x = Number(v)
  return Number.isFinite(x) ? x : d
}

/** Probe once, release the probe context immediately — never leak 8× contexts. */
let webglOk: boolean | null = null
export function canUseWebGL(): boolean {
  if (webglOk != null) return webglOk
  try {
    const c = document.createElement('canvas')
    const gl =
      (c.getContext('webgl2', { failIfMajorPerformanceCaveat: false }) as WebGLRenderingContext | null) ||
      (c.getContext('webgl', { failIfMajorPerformanceCaveat: false }) as WebGLRenderingContext | null) ||
      (c.getContext('experimental-webgl') as WebGLRenderingContext | null)
    if (!gl) {
      webglOk = false
      return false
    }
    const lose = gl.getExtension('WEBGL_lose_context')
    lose?.loseContext()
    webglOk = true
    return true
  } catch {
    webglOk = false
    return false
  }
}

const leafTexCache = new Map<string, THREE.CanvasTexture>()

function getLeafTexture(stage: string): THREE.CanvasTexture {
  const key = stage === 'R1' || stage === 'VT' ? 'mature' : 'green'
  const hit = leafTexCache.get(key)
  if (hit) return hit

  const c = document.createElement('canvas')
  c.width = 512
  c.height = 128
  const g = c.getContext('2d')!

  const grd = g.createLinearGradient(0, 0, 512, 0)
  if (key === 'mature') {
    grd.addColorStop(0, '#5f7a28')
    grd.addColorStop(0.4, '#b8973a')
    grd.addColorStop(0.75, '#d4af55')
    grd.addColorStop(1, '#e8c978')
  } else {
    grd.addColorStop(0, '#1a4d22')
    grd.addColorStop(0.25, '#287534')
    grd.addColorStop(0.55, '#3d9a45')
    grd.addColorStop(0.85, '#5bb85a')
    grd.addColorStop(1, '#8fd070')
  }
  g.fillStyle = grd
  g.fillRect(0, 0, 512, 128)

  // subtle mottling
  for (let i = 0; i < 40; i++) {
    g.fillStyle = key === 'mature' ? 'rgba(180,140,40,0.12)' : 'rgba(30,90,40,0.1)'
    g.beginPath()
    g.ellipse(Math.random() * 512, Math.random() * 128, 18 + Math.random() * 30, 6 + Math.random() * 10, 0, 0, Math.PI * 2)
    g.fill()
  }

  // midrib
  g.strokeStyle = 'rgba(255,255,230,0.4)'
  g.lineWidth = 4
  g.beginPath()
  g.moveTo(6, 64)
  g.quadraticCurveTo(256, 58, 506, 64)
  g.stroke()

  // parallel veins
  g.strokeStyle = key === 'mature' ? 'rgba(90,70,20,0.2)' : 'rgba(20,55,25,0.22)'
  g.lineWidth = 1.2
  for (let i = 1; i < 12; i++) {
    const x = 24 + i * 40
    g.beginPath()
    g.moveTo(x, 64)
    g.quadraticCurveTo(x + 14, 36, x + 36, 18)
    g.moveTo(x, 64)
    g.quadraticCurveTo(x + 14, 92, x + 36, 110)
    g.stroke()
  }

  // soft edge alpha falloff via destination-in
  const edge = g.createLinearGradient(0, 0, 0, 128)
  edge.addColorStop(0, 'rgba(0,0,0,0.15)')
  edge.addColorStop(0.15, '#000')
  edge.addColorStop(0.85, '#000')
  edge.addColorStop(1, 'rgba(0,0,0,0.15)')
  g.globalCompositeOperation = 'destination-in'
  g.fillStyle = edge
  g.fillRect(0, 0, 512, 128)
  g.globalCompositeOperation = 'source-over'

  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.anisotropy = 4
  leafTexCache.set(key, tex)
  return tex
}

function makeLeafGeo(len: number, width: number): THREE.BufferGeometry {
  const segsU = 28
  const segsV = 6
  const positions: number[] = []
  const uvs: number[] = []
  const indices: number[] = []

  for (let i = 0; i <= segsU; i++) {
    const u = i / segsU
    const x = len * u
    // corn leaf: rises then droops
    const lift = Math.sin(u * Math.PI * 0.92) * len * 0.16 - Math.pow(u, 2.1) * len * 0.32
    const taper = Math.pow(Math.sin(Math.max(0.02, u) * Math.PI), 0.65)
    const half = width * 0.5 * (1 - u * 0.92) * (0.28 + 0.72 * taper)
    for (let j = 0; j <= segsV; j++) {
      const v = j / segsV
      const z = THREE.MathUtils.lerp(-half, half, v)
      // V-shaped cross section (midrib fold)
      const fold = -Math.abs(v - 0.5) * 2 * half * 0.22
      const ripple = Math.sin(u * Math.PI * 3 + v * 2) * 0.004 * (1 - u)
      positions.push(x, lift + fold + ripple, z)
      uvs.push(u, v)
    }
  }
  for (let i = 0; i < segsU; i++) {
    for (let j = 0; j < segsV; j++) {
      const a = i * (segsV + 1) + j
      indices.push(a, a + 1, a + segsV + 1, a + 1, a + segsV + 2, a + segsV + 1)
    }
  }
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2))
  geo.setIndex(indices)
  geo.computeVertexNormals()
  return geo
}

function buildPresentationCorn(specimen: PlantSpecimen): THREE.Group {
  const latest = specimen.latest || {}
  const heightCm = clamp(n(latest.height_cm, 55), 18, 140)
  const leafCount = clamp(Math.round(n(latest.leaf_count, 9)), 4, 14)
  const stemMm = clamp(n(latest.stem_diameter_mm, 14), 8, 28)
  const stage = String(specimen.growthStage || 'V6')
  const mature = stage === 'VT' || stage === 'R1'
  // stable per-plant variation from plantCode
  const seed = String(specimen.plantCode || 'P').split('').reduce((a, ch) => a + ch.charCodeAt(0), 0)
  const jitter = (i: number) => ((Math.sin(seed * 12.9898 + i * 78.233) * 43758.5453) % 1 + 1) % 1

  const root = new THREE.Group()

  // Terracotta pot
  const potMat = new THREE.MeshStandardMaterial({
    color: '#a06d4c',
    roughness: 0.62,
    metalness: 0.06,
  })
  const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.128, 0.1, 0.105, 36), potMat)
  pot.position.y = 0.052
  pot.castShadow = true
  pot.receiveShadow = true
  root.add(pot)
  const rim = new THREE.Mesh(
    new THREE.TorusGeometry(0.13, 0.011, 12, 36),
    new THREE.MeshStandardMaterial({ color: '#c09068', roughness: 0.4, metalness: 0.08 }),
  )
  rim.rotation.x = Math.PI / 2
  rim.position.y = 0.105
  root.add(rim)
  const soil = new THREE.Mesh(
    new THREE.CircleGeometry(0.118, 36),
    new THREE.MeshStandardMaterial({ color: '#2e2018', roughness: 1 }),
  )
  soil.rotation.x = -Math.PI / 2
  soil.position.y = 0.106
  soil.receiveShadow = true
  root.add(soil)

  // Curved stem
  const stemH = 0.52 + heightCm * 0.009
  const stemR = 0.015 + stemMm * 0.00095
  const lean = (jitter(1) - 0.5) * 0.04
  const curve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0, 0.105, 0),
    new THREE.Vector3(lean, 0.105 + stemH * 0.33, lean * 0.4),
    new THREE.Vector3(-lean * 0.6, 0.105 + stemH * 0.68, -lean * 0.3),
    new THREE.Vector3(lean * 0.2, 0.105 + stemH, 0),
  ])
  const stemMat = new THREE.MeshStandardMaterial({
    color: mature ? '#7d8e36' : '#355f28',
    roughness: 0.78,
    metalness: 0.03,
  })
  const stem = new THREE.Mesh(new THREE.TubeGeometry(curve, 32, stemR, 12, false), stemMat)
  stem.castShadow = true
  root.add(stem)

  const nodeMat = new THREE.MeshStandardMaterial({ color: '#2a4a1e', roughness: 0.85 })
  for (let i = 1; i < leafCount; i++) {
    const t = i / leafCount
    const p = curve.getPoint(t)
    const node = new THREE.Mesh(new THREE.SphereGeometry(stemR * 1.4, 12, 10), nodeMat)
    node.position.copy(p)
    node.scale.set(1, 0.5, 1)
    root.add(node)
  }

  const leafTex = getLeafTexture(stage)
  const leafMat = new THREE.MeshPhysicalMaterial({
    map: leafTex,
    roughness: 0.48,
    metalness: 0.0,
    sheen: 0.35,
    sheenColor: new THREE.Color(mature ? '#d4b060' : '#8bc96a'),
    sheenRoughness: 0.55,
    side: THREE.DoubleSide,
    transparent: true,
    alphaTest: 0.08,
  })

  for (let i = 0; i < leafCount; i++) {
    const t = (i + 0.55) / (leafCount + 0.25)
    const p = curve.getPoint(clamp(t, 0.1, 0.97))
    const len = 0.3 + (1 - Math.abs(t - 0.42)) * 0.3 + heightCm * 0.0011 + jitter(i + 3) * 0.04
    const width = 0.078 + (1 - t) * 0.05 + jitter(i + 7) * 0.012
    const leaf = new THREE.Mesh(makeLeafGeo(len, width), leafMat)
    const side = i % 2 === 0 ? 1 : -1
    const yaw = side * (0.65 + (i % 5) * 0.15) + i * 0.38 + (jitter(i) - 0.5) * 0.25
    leaf.position.copy(p)
    leaf.rotation.order = 'YXZ'
    leaf.rotation.y = yaw
    leaf.rotation.z = side * (0.15 + t * 0.28)
    leaf.rotation.x = -0.18 - t * 0.4 - jitter(i + 11) * 0.08
    leaf.castShadow = true
    root.add(leaf)
  }

  // Growing tip / tassel
  const tip = curve.getPoint(1)
  if (mature) {
    const tasselMat = new THREE.MeshStandardMaterial({ color: '#e2b84c', roughness: 0.6 })
    for (let i = 0; i < 11; i++) {
      const spike = new THREE.Mesh(new THREE.ConeGeometry(0.0055, 0.095 + jitter(i) * 0.03, 5), tasselMat)
      const a = (i / 11) * Math.PI * 2
      const r = 0.014 + (i % 3) * 0.004
      spike.position.set(tip.x + Math.cos(a) * r, tip.y + 0.055, tip.z + Math.sin(a) * r)
      spike.rotation.z = Math.cos(a) * 0.4
      spike.rotation.x = Math.sin(a) * 0.4
      root.add(spike)
    }
  } else {
    const bud = new THREE.Mesh(
      new THREE.SphereGeometry(stemR * 1.6, 12, 10),
      new THREE.MeshStandardMaterial({ color: '#4ea34a', roughness: 0.55 }),
    )
    bud.position.copy(tip)
    bud.position.y += 0.012
    bud.scale.set(1, 1.35, 1)
    root.add(bud)
  }

  if (stage === 'R1') {
    const earMat = new THREE.MeshStandardMaterial({ color: '#f0d56a', roughness: 0.45 })
    const huskMat = new THREE.MeshStandardMaterial({
      color: '#4a8a38',
      roughness: 0.62,
      side: THREE.DoubleSide,
    })
    const mid = curve.getPoint(0.52)
    const ear = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.026, 0.11, 12), earMat)
    ear.position.set(mid.x + 0.065, mid.y, mid.z)
    ear.rotation.z = 0.95
    ear.castShadow = true
    root.add(ear)
    const husk = new THREE.Mesh(new THREE.ConeGeometry(0.042, 0.15, 8, 1, true), huskMat)
    husk.position.copy(ear.position)
    husk.rotation.z = 0.95
    root.add(husk)
  }

  const box = new THREE.Box3().setFromObject(root)
  const size = box.getSize(new THREE.Vector3())
  const s = size.y > 0.001 ? 1.08 / size.y : 1
  root.scale.setScalar(s)
  box.setFromObject(root)
  const c = box.getCenter(new THREE.Vector3())
  root.position.x -= c.x
  root.position.z -= c.z
  root.position.y -= box.min.y
  return root
}

type Slot = {
  canvas: HTMLCanvasElement
  ctx: CanvasRenderingContext2D
  scene: THREE.Scene
  camera: THREE.PerspectiveCamera
  plant: THREE.Object3D | null
  yaw: number
  autoSpin: boolean
  dragging: boolean
  dirty: boolean
}

class SharedCornRenderer {
  private renderer: THREE.WebGLRenderer
  private host: HTMLDivElement
  private slots = new Set<Slot>()
  private raf = 0
  private running = false

  constructor() {
    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
      powerPreference: 'high-performance',
      preserveDrawingBuffer: true,
      failIfMajorPerformanceCaveat: false,
    })
    this.renderer.setClearColor(0x000000, 0)
    this.renderer.shadowMap.enabled = true
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap
    this.renderer.outputColorSpace = THREE.SRGBColorSpace
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping
    this.renderer.toneMappingExposure = 1.15

    this.host = document.createElement('div')
    this.host.setAttribute('aria-hidden', 'true')
    this.host.style.cssText =
      'position:fixed;left:-9999px;top:0;width:1px;height:1px;overflow:hidden;opacity:0;pointer-events:none;'
    this.host.appendChild(this.renderer.domElement)
    document.body.appendChild(this.host)
  }

  add(slot: Slot) {
    this.slots.add(slot)
    this.ensureLoop()
  }

  remove(slot: Slot) {
    this.slots.delete(slot)
    if (this.slots.size === 0) {
      cancelAnimationFrame(this.raf)
      this.running = false
    }
  }

  private ensureLoop() {
    if (this.running) return
    this.running = true
    const tick = () => {
      if (!this.running) return
      for (const slot of this.slots) {
        if (slot.autoSpin && !slot.dragging) {
          slot.yaw += 0.006
          slot.dirty = true
        }
        if (slot.dirty || slot.dragging) {
          this.renderSlot(slot)
          slot.dirty = false
        }
      }
      this.raf = requestAnimationFrame(tick)
    }
    this.raf = requestAnimationFrame(tick)
  }

  renderSlot(slot: Slot) {
    const { canvas, ctx, scene, camera, plant } = slot
    const w = Math.max(2, canvas.clientWidth || 200)
    const h = Math.max(2, canvas.clientHeight || 280)
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    if (plant) plant.rotation.y = slot.yaw
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    this.renderer.setPixelRatio(dpr)
    this.renderer.setSize(w, h, false)
    this.renderer.render(scene, camera)
    const src = this.renderer.domElement
    if (canvas.width !== src.width || canvas.height !== src.height) {
      canvas.width = src.width
      canvas.height = src.height
    }
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(src, 0, 0)
  }
}

let shared: SharedCornRenderer | null = null
function getShared() {
  if (!shared) shared = new SharedCornRenderer()
  return shared
}

function buildStudio(): { scene: THREE.Scene; camera: THREE.PerspectiveCamera } {
  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(28, 1, 0.05, 40)
  camera.position.set(1.0, 0.92, 1.85)
  camera.lookAt(0, 0.5, 0)

  scene.add(new THREE.HemisphereLight(0xf8fff6, 0x7a6550, 0.75))

  const key = new THREE.DirectionalLight(0xfff3e4, 1.5)
  key.position.set(2.4, 5.2, 2.8)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  key.shadow.bias = -0.00025
  key.shadow.camera.near = 0.5
  key.shadow.camera.far = 16
  scene.add(key)

  const rim = new THREE.DirectionalLight(0xb8d4ff, 0.55)
  rim.position.set(-2.6, 2.4, -1.8)
  scene.add(rim)

  const fill = new THREE.DirectionalLight(0xffffff, 0.28)
  fill.position.set(0.2, 1.2, 3.5)
  scene.add(fill)

  const pedestal = new THREE.Mesh(
    new THREE.CylinderGeometry(0.34, 0.36, 0.028, 48),
    new THREE.MeshStandardMaterial({ color: '#eef2ee', roughness: 0.45, metalness: 0.04 }),
  )
  pedestal.position.y = 0.014
  pedestal.receiveShadow = true
  scene.add(pedestal)

  const shadow = new THREE.Mesh(
    new THREE.CircleGeometry(0.3, 48),
    new THREE.MeshBasicMaterial({ color: 0x102018, transparent: true, opacity: 0.14, depthWrite: false }),
  )
  shadow.rotation.x = -Math.PI / 2
  shadow.position.y = 0.03
  scene.add(shadow)

  return { scene, camera }
}

function disposeObject(obj: THREE.Object3D) {
  obj.traverse((child) => {
    const mesh = child as THREE.Mesh
    if (!mesh.isMesh) return
    mesh.geometry?.dispose()
    const mat = mesh.material
    // Do not dispose shared leaf textures
    if (Array.isArray(mat)) mat.forEach((m) => m.dispose())
    else mat?.dispose()
  })
}

export function createCornPlantThree(canvas: HTMLCanvasElement, specimen: PlantSpecimen): CornPlantView {
  const ctx = canvas.getContext('2d', { alpha: true })
  if (!ctx) throw new Error('2d context unavailable')

  const { scene, camera } = buildStudio()
  const engine = getShared()
  const slot: Slot = {
    canvas,
    ctx,
    scene,
    camera,
    plant: null,
    yaw: 0.3 + Math.random() * 0.7,
    autoSpin: true,
    dragging: false,
    dirty: true,
  }

  let current = specimen
  let disposed = false
  let moved = false
  let lastX = 0

  const ro = new ResizeObserver(() => {
    slot.dirty = true
  })
  ro.observe(canvas)

  function mountPlant(sp: PlantSpecimen) {
    if (slot.plant) {
      scene.remove(slot.plant)
      disposeObject(slot.plant)
    }
    slot.plant = buildPresentationCorn(sp)
    scene.add(slot.plant)
    slot.dirty = true
  }

  const view: CornPlantView = {
    onSelect: null,
    setSpecimen(sp) {
      current = sp
      mountPlant(sp)
    },
    destroy() {
      disposed = true
      ro.disconnect()
      canvas.removeEventListener('pointerdown', onDown)
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      engine.remove(slot)
      if (slot.plant) {
        scene.remove(slot.plant)
        disposeObject(slot.plant)
      }
      scene.traverse((c) => {
        const m = c as THREE.Mesh
        if (!m.isMesh) return
        m.geometry?.dispose()
        const mat = m.material
        if (Array.isArray(mat)) mat.forEach((x) => x.dispose())
        else (mat as THREE.Material)?.dispose?.()
      })
    },
  }

  function onDown(e: PointerEvent) {
    slot.dragging = true
    slot.autoSpin = false
    moved = false
    lastX = e.clientX
    canvas.setPointerCapture?.(e.pointerId)
  }
  function onMove(e: PointerEvent) {
    if (!slot.dragging) return
    const dx = e.clientX - lastX
    if (Math.abs(dx) > 2) moved = true
    slot.yaw += dx * 0.012
    lastX = e.clientX
    slot.dirty = true
  }
  function onUp() {
    if (!slot.dragging) return
    slot.dragging = false
    if (!moved && typeof view.onSelect === 'function') view.onSelect(current)
    window.setTimeout(() => {
      if (!disposed) slot.autoSpin = true
    }, 1600)
  }

  canvas.addEventListener('pointerdown', onDown)
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)

  mountPlant(specimen)
  engine.add(slot)
  // force a few redraws after layout settles
  requestAnimationFrame(() => {
    slot.dirty = true
    requestAnimationFrame(() => {
      slot.dirty = true
    })
  })
  return view
}

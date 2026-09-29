/// <reference types="vite/client" />

declare module '@/components/plant/corn3d.js' {
  export const CornPlant3D: {
    create: (canvas: HTMLCanvasElement, specimen: any) => {
      angle: number
      onSelect: ((specimen: any) => void) | null
      setSpecimen: (specimen: any) => void
      destroy: () => void
      _resize: () => void
    }
  }
}

declare module '*.js' {
  const value: any
  export default value
}

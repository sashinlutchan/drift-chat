import { create } from 'zustand'
export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting' | 'offline'

interface ConnectionState {
  status: ConnectionStatus
  setStatus: (status: ConnectionStatus) => void
}

export const useConnection = create<ConnectionState>((set) => ({
  status: 'connecting',
  setStatus: (status) => set({ status }),
}))

import { create } from 'zustand'

export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting' | 'offline'

interface ConnectionState {
  status: ConnectionStatus
  setStatus: (status: ConnectionStatus) => void
}

/** Socket health, for the "Reconnecting…" banner (spec section 10). */
export const useConnection = create<ConnectionState>((set) => ({
  status: 'connecting',
  setStatus: (status) => set({ status }),
}))

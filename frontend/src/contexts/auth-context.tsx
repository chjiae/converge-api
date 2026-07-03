import { createContext, useContext, useState, useCallback } from "react"

interface User {
  id: string
  name: string
  email: string
}

interface AuthContextType {
  user: User | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<boolean>
  register: (name: string, email: string, password: string) => Promise<boolean>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)

  const login = useCallback(async (email: string, _password: string): Promise<boolean> => {
    // TODO: Replace with real API call
    await new Promise((r) => setTimeout(r, 800))

    if (email && _password) {
      setUser({
        id: "1",
        name: email.split("@")[0],
        email,
      })
      return true
    }
    return false
  }, [])

  const register = useCallback(async (name: string, email: string, _password: string): Promise<boolean> => {
    // TODO: Replace with real API call
    await new Promise((r) => setTimeout(r, 1000))

    if (name && email && _password) {
      setUser({
        id: crypto.randomUUID(),
        name,
        email,
      })
      return true
    }
    return false
  }, [])

  const logout = useCallback(() => {
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider
      value={{ user, isAuthenticated: !!user, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}

import { Routes, Route } from "react-router-dom"
import HomePage from "@/pages/home"
import LoginPage from "@/pages/login"
import RegisterPage from "@/pages/register"
import ConsolePage from "@/pages/console"

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/console" element={<ConsolePage />} />
    </Routes>
  )
}

export default App

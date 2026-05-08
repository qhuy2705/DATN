import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import "./index.css";
import "./i18n";

try {
  const stored = localStorage.getItem("primecare-theme");
  if (stored) {
    const parsed = JSON.parse(stored);
    if (parsed?.state?.theme === "dark") {
      document.documentElement.classList.add("dark");
    }
  }
} catch (error) {
  console.warn("Invalid primecare-theme in localStorage, clearing it.", error);
  localStorage.removeItem("primecare-theme");
}

createRoot(document.getElementById("root")!).render(<App />);
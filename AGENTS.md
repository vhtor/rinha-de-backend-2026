# Agent Instructions

This file contains instructions for AI agents working on this project.
Full instructions, context, and ADR convention are in `.github/copilot-instructions.md`.

## TL;DR

- **Learning-first:** always explain what, why, and what alternatives were considered before implementing anything
- **Document decisions:** every architectural/technical decision → ADR in the Obsidian vault at `Rinha de Backend 2026/📔 Progresso/🗺️ Tarefas/Fase {N}/📝 ADR-{NNN} {Title}.md`
- **Port:** 9999 (not 8080)
- **Stack:** Kotlin + Ktor CIO + Gradle Kotlin DSL + GraalVM Native Image + Nginx
- **Budget:** 1 CPU + 350 MB RAM total across all containers

See `.github/copilot-instructions.md` for the full picture.

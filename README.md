# Java_Game

# Cyber Hockey 2026

Një lojë moderne air-hockey (hokej ajror) e shkruar në Java duke përdorur Swing. Loja ofron grafikë neon me efekte glow, fizikë realiste, disa mënyra loje dhe nivele progresive.

## Përshkrimi

Cyber Hockey 2026 është një version futuristik i lojës klasike air-hockey. Në vend të golave të zakonshëm në anët e majta/djathta, loja mbështet dy orientime:

- **Horizontale** (2 lojtarë): golat janë në anën e majtë dhe të djathtë – si air-hockey tradicional.
- **Vertikale** (kundër bot-it ose nivele): golat janë lart dhe poshtë, ku çdo lojtar mbron gjysmën e vet të fushës.

Loja përfshin:
- Efekte vizuale neon me gradient dhe glow
- Fizikë me spin dhe përshpejtim pas goditjes
- Bot me 3 nivele vështirësie (Easy, Medium, Hard)
- Sistem nivelesh me pengesa lëvizëse, kufi kohe dhe rritje të shpejtësisë
- Përshtatje automatike me madhësinë e dritares (mbështet fullscreen dhe resize)

## Mënyrat e lojës

1. **2 Lojtarë (Horizontal)** – Lojë lokale me dy lojtarë (WASD për lojtarin 1, shigjetat për lojtarin 2).
2. **Kundër Bot-it** – Easy / Medium / Hard (fusha vertikale).
3. **Nivele** – 3 nivele progresive me pengesa dhe kufi kohe 1:30. Nivelet zhbllokohen një nga një.

Fitorja në mënyrën normale është në 7 gola. Në nivele, numri i nevojshëm i golave ndryshon sipas nivelit.

## Kontrollet

- **Lojtari 1**: W A S D
- **Lojtari 2** (në PvP): Shigjetat (← ↑ → ↓)
- **ESC** – Kthim në menunë kryesore në çdo moment

## Si të luhet

1. Ekzekutoni klasën `Main`.
2. Zgjidhni mënyrën e lojës nga menuja që shfaqet.
3. Luani derisa një palë të arrijë numrin e nevojshëm të golave ose të mbarojë koha (në nivele).

## Kërkesat teknike

- Java 8 ose më e lartë
- Asnjë varësi e jashtme – gjithçka është në kodin e vetëm

## Si të kompiloni dhe ekzekutoni

```bash
javac Main.java
java Main
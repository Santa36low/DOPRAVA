# Doprava – Android

Základní offline aplikace pro evidenci činností řidiče kamionu.

## Obsah této verze
- Činnosti: Jízda, Nákladka, Výkladka, Pauza a Cesta domů.
- Jedna aktivní činnost; opětovné klepnutí ji ukončí.
- Lokální SQLite databáze pro činnosti, tankování a body GPS trasy.
- Časovač HH:MM:SS.
- Při jízdě se pokouší zaznamenávat body trasy přes foreground location service.
- GitHub Actions workflow pro sestavení debug APK.

## Důležité
Jde o počáteční funkční základ, nikoli dokončenou aplikaci. Vyžaduje kontrolu oprávnění polohy v Androidu. Současná verze ještě nemá vyhledávání firem v okolí, ruční výběr místa v mapě, kompletní editaci všech polí záznamu ani export/import. Záznam polohy na pozadí závisí na udělených oprávněních a nastavení baterie telefonu.

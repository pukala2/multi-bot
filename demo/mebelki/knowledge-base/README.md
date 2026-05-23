# Baza wiedzy Mebelki.pl

Fikcyjne dokumenty firmy Mebelki Sp. z o.o. do wgrania jako baza wiedzy bota.

W produkcji to **tenant** (operator Mebelków) wgrywa swoje dokumenty przez
admin panel. W naszym demo te pliki naśladują "wewnętrzne dokumenty Mebelków".

## Pliki

| Plik | Zawiera | Rozmiar |
|------|---------|---------|
| `regulamin.txt` | §1-§10: dane firmy, zamówienia, płatności, dostawa, zwroty, gwarancja, program Mebelki Klub, RODO | ~5 KB |
| `faq.txt` | 15 najczęstszych pytań: drewno, dostawa, montaż, personalizacja, pielęgnacja, raty, showroom | ~5 KB |
| `katalog-produktow.md` | Szczegóły 6 produktów (kody, wymiary, ceny) + nadchodząca kolekcja Q2 2026 | ~4 KB |
| `dostawa-i-montaz.txt` | Strefy, cennik dostawy, montaż, wnoszenie, utylizacja, harmonogram | ~5 KB |

Łącznie: ~19 KB tekstu, ~80 chunków po zaindeksowaniu (~$0.002 jednorazowo
na embeddingi w OpenAI).

## Jak wgrać do bota

1. **Uruchom backend** (port 8080) i **admin panel** (port 5173).
2. Otwórz `http://localhost:5173/dokumenty`.
3. Przeciągnij wszystkie 4 pliki kolejno na obszar uploadu
   (albo użyj button "Wybierz plik").
4. Poczekaj ~3-5 sekund na każdy - status leci `Oczekuje → Przetwarzanie → Gotowe`.
5. Sprawdź w `http://localhost:8001` że bot na Mebelki.pl jest dostępny.

## Test scenariusze

Po wgraniu wszystkich 4 plików, otwórz widget na `localhost:8001` i zadaj:

| Pytanie | Oczekiwana odpowiedź | Skąd? |
|---------|----------------------|-------|
| *"Ile kosztuje sofa Wenecja?"* | **3 290 zł** | katalog-produktow.md |
| *"Z jakiego drewna są krzesła Sosnowiec?"* | **lita sosna z lasów Bieszczad** | katalog-produktow.md + faq |
| *"Czy mogę zwrócić mebel po montażu?"* | **NIE** (art. 38 pkt 3 ustawy) | regulamin.txt §6 |
| *"Ile kosztuje dostawa stołu do Warszawy?"* | **199 zł** (STREFA 2, kategoria stoły) | dostawa-i-montaz.txt |
| *"Ile lat gwarancji na konstrukcję?"* | **5 lat** | regulamin.txt §7 + faq |
| *"Czy dostarczacie do Niemiec?"* | **Tak, dopłata 199 zł, 3-7 dni** | regulamin §4 + dostawa |
| *"Co to MEB-CHAIR-SOSN-001?"* | **Krzesło Sosnowiec**, 449 zł | katalog-produktow.md |
| *"Jakie macie raty?"* | 6/12/24 mc, RRSO 0%-9.9%, partner Aion Bank | regulamin + faq |
| *"Kim jest Natalia Drzewińska?"* | Stylista wnętrz, ASP Kraków, 12 lat doświadczenia | faq #10 |

## Pytania kontrolne (out of domain)

Bot powinien grzecznie odmówić odpowiedzi - to dowód że RAG działa
i bot nie halucynuje:

| Pytanie | Oczekiwana odpowiedź |
|---------|----------------------|
| *"Ile waży Wieża Eiffla?"* | "Nie znajduję w dokumentach Mebelków..." |
| *"Kim jest Adam Mickiewicz?"* | Grzeczna odmowa (poza domeną) |
| *"Co to Kosmiczny Pomidor?"* | "Nie ma takiego produktu w katalogu Mebelków" |

## Czyszczenie / reset

Aby usunąć dokumenty Mebelków z bazy (np. żeby przetestować od zera):

1. W admin panelu `/dokumenty` kliknij ikonę kosza przy każdym
2. Albo SQL bezpośrednio:
   ```sql
   DELETE FROM documents WHERE filename LIKE '%mebelki%'
     OR filename LIKE '%regulamin%'
     OR filename LIKE '%faq%'
     OR filename LIKE '%katalog%'
     OR filename LIKE '%dostawa%';
   ```

Po usunięciu - bot wraca do stanu "nie wie nic o Mebelkach".

placeholder
icon:fa-solid fa-plug
route:info-pl
krz  :Czym jest taksiegra?
title:Czym jest taksiegra?
navbar:false
footer:true
behindLogin:false

> 🇬🇧 [English version](info?lang=en)

Szczerze mówiąc, z własnej inicjatywy chciałem spróbować postawić swój homelab — jak widać na https://taksiegra.ovh/changelog, w końcu mi się udało. Nazwę taksiegra wymyślił w zasadzie Pioye_, o ile dobrze pamiętam po wbiciu jakiegoś szalonego wyniku i krótkiej rozmowie o tym, jak nazwać stronę.

Po postawieniu strony długo niewiele się tu działo — aż zacząłem bawić się Cloudflare Tunnel. Dzięki niemu i oczywiście AI udało mi się postawić serwer osu! o nazwie taksiegra, z nginx jako reverse proxy i rekordami CNAME w DNS. Pierwsza wersja zajęła mi około trzech dni w święta 2025 (mniej więcej 22,5 godziny realnej pracy), a na koniec wszystko działa świetnie. Rozwojem zajmuję się sam, czasem testując rzeczy z AI.

## Jak powstał serwer osu!

Wszystkie godziny są czasem polskim i pochodzą z mojej historii czatu z tamtych dni.

### Punkt wyjścia

Moje domowe łącze jest za CGNAT, więc nie mam publicznego IP ani przekierowania portów. Oficjalny poradnik osu!NoLimits zakłada rekordy A wskazujące na IP serwera, a tego nie mogłem zrobić. Zamiast tego każda subdomena (`osu`, `api`, `c`, `c4`, `a`, później `b`) idzie przez Cloudflare Tunnel jako CNAME, a nginx na serwerze rozdziela ruch.

Na czym to stoi:

- **bancho.py-ex** w Dockerze (bancho, MySQL, Redis), API na porcie 10000
- **Shiina-Web** (Java, Maven) jako strona, na porcie 3010
- **nginx** jako reverse proxy dla wszystkiego
- **Cloudflare Tunnel** zamiast publicznego IP

### Noc z 24 na 25 grudnia — od zera do pierwszego logowania (23:23–03:22)

W wigilię wróciłem do starego pomysłu: wcześniejsza próba postawienia serwera osu! padła na sieci, nie na kodzie. Tym razem plan był taki: Cloudflare Tunnel i poradnik osu!NoLimits, 1:1, razem z Dockerem.

- **00:05** — pierwszy vhost dla `osu.taksiegra.ovh` odpowiada przez nginx.
- **00:14** — tunel łapie `*.taksiegra.ovh` i przekazuje ruch do nginx.
- **00:38** — `make run`: startują bancho, MySQL i Redis, a API odpowiada przez Cloudflare.
- **00:50** — Shiina-Web buduje się po doinstalowaniu Mavena, strona działa.
- **01:09** — MySQL i Redis zamknięte na `127.0.0.1`, po kilku nieudanych próbach edycji `docker-compose.yml`.
- **01:54** — pierwsze logowanie klientem gry przez `-devserver osu.taksiegra.ovh`. Moja reakcja: *„MAMY TO”* (z kilkoma dodatkowymi słowami).
- **02:13** — avatary na `a.taksiegra.ovh`, z domyślnym avatarem dla każdego nowego konta.
- **02:41** — wszystko przeniesione do `/mnt/storage/osu/`, żebym miał dostęp do plików z Windowsa.
- **03:22** — favicona i spać.

### 25 grudnia — 13-godzinny maraton (13:21 – 02:19)

Najdłuższy dzień. Rano strona i avatary, po południu osu!direct, a wieczorem i w nocy wysyłanie wyników.

- **13:33–14:54** — zmiana avatara na stronie się wywalała. Poprawiłem sprawdzanie rozszerzeń pliku (dwie nieudane kompilacje przez nawiasy), a potem brak avatara na profilu, zmieniając `AVATARSRV`.
- **15:00–15:24** — bancho i Shiina-Web jako usługi systemd, żeby startowały same po restarcie.
- **15:27–15:47** — brak avatarów w samym kliencie gry: klient pyta o `/3`, a nie `/avatars/3`. Poprawione w nginx.
- **15:53–17:04** — osu!direct wisiał na „Searching”. Dwie przyczyny: system zabijał Redisa (naprawione przez `vm.overcommit_memory=1`), a router `/web/` był w kodzie, ale nigdy nie został podpięty do API.
- **17:07–18:11** — bancho przekierowywał pobieranie map na osu.direct, który zrzucał HTTPS na HTTP. Zrobiłem własny proxy pobierania w nginx pod `c.taksiegra.ovh/d/`.
- **18:25–19:18** — mapy w końcu pojawiły się w osu!direct, ale po kilkunastu zagranych wszystko dalej było na 0, a wyniki się nie wysyłały.
- **19:45–20:06** — pobrane mapy były „uszkodzone”. Mirror wysyłał klientowi małą stronę zamiast pliku `.osz` przez User-Agenta `osu!`. Naprawione wysyłaniem innego User-Agenta z proxy.
- **20:13–23:26** — długa walka z wysyłaniem wyników: stub `osu-session.php` w nginx, wiele wersji configu, błędy walidacji (422) przeplatane z pierwszymi udanymi wynikami.
- **00:01** — pierwszy zapisany wynik: 14,29 pp. Chwilę później 92,34 pp.
- **01:03** — potwierdzenie, że wysyłanie działa: *„submitted a score! (Best, 118.05pp / 265pp)”*. Kluczem było przepuszczenie selectora wysyłania do bancho zamiast odpowiadania na niego bezpośrednio w nginx.
- **01:16–02:19** — pobierane mapy dalej wychodziły uszkodzone. Zostawione na następny dzień.

### 26 grudnia — pobieranie, podgląd muzyki i tła (14:08–21:33)

- **14:08–14:40** — nowy, pełny config nginx na chwilę zepsuł wysyłanie wyników. Połączyłem go z działającą wersją i wysyłanie wróciło.
- **14:54–16:01** — pobieranie naprawione na dobre: zapasowy mirror, gdy główny zwraca 451/404, oraz poprawione nagłówki `Content-Disposition` i `Accept-Ranges`. Mapy przestały lądować w folderze „Failed”.
- **16:09–17:51** — nowa subdomena `b.taksiegra.ovh` na podgląd muzyki, tła i miniatury w osu!direct (znowu CNAME przez tunel).
- **19:07–20:47** — tła są brane z assets.ppy.sh, a miniatury mają rozmiar 80×60. Kilka pierwszych kafelków zostało za dużych przez cache klienta, więc na tym skończyłem.
- **20:51–21:33** — próba z IRC przez ZeroTier na porcie 6667. Z włączonym IRC bancho nie startował (`socket.gaierror`) i kontener ciągle się restartował, więc na razie odpuściłem.

### Ile to zajęło

Łącznie **ok. 22,5 godziny** w pięciu posiedzeniach:

- **Posiedzenie 1:** 24.12, 23:23 – 25.12, 03:22 (4 h)
- **Posiedzenie 2:** 25.12, 13:21 – 26.12, 02:19 (13 h)
- **Posiedzenie 3:** 26.12, 14:08 – 17:51 (3 h)
- **Posiedzenie 4:** 26.12, 19:07 – 21:33 (2,5 h)
- **Posiedzenie 5:** 27.12, 00:35 – 00:53, pisanie changelogu (20 min)

### Co było dalej z IRC

IRC wróciło po około siedmiu tygodniach. **12 lutego 2026** zdecydowałem się na IRC zamiast WebSocketu, bo serwer miał niedługo dostać publiczne IP, a to właśnie CGNAT blokował w grudniu port 6667. **6 marca 2026** już działało: `!mp make` i `!mp close` z klienta IRC, synchronizacja czatu z `#osu` i `#multi_*`, timery meczów i masa poprawek crashy. Do **12 marca** zostało dopracowane (logowanie, odpowiedzi BanchoBota po ponownym połączeniu, zdublowane wiadomości).

### Na czym stanęło

Po tych trzech dniach serwer był gotowy dla graczy: logowanie, strona, avatary, osu!direct z pobieraniem, podglądem muzyki i tłami oraz wysyłanie wyników z pp. IRC dołączyło w marcu 2026. Wszystko, co działo się później, jest w [changelogu](https://taksiegra.ovh/changelog).

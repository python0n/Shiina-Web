placeholder
icon:fa-solid fa-server
route:connect-pl
krz  :Połączenie
title:Jak się połączyć
navbar:false
footer:false
behindLogin:false

# Jak połączyć się z taksiegra

### 1. Załóż konto
Zarejestruj się na **https://osu.taksiegra.ovh**. Rejestracja z poziomu gry jest wyłączona.

### 2. Uruchom osu! z flagą devserver

Zostajesz przy swojej zwykłej instalacji osu!, skinach i mapach. Flaga zmienia tylko serwer, z którym łączy się gra.

**Windows (automatycznie)**

Otwórz PowerShell (naciśnij Win + X, potem „Terminal” lub „Windows PowerShell”) i wklej:
```
[Net.ServicePointManager]::SecurityProtocol = 'Tls12'; irm https://taksiegra.ovh/downloads/setuptaksiegra.ps1 | iex
```
Skrypt znajdzie twoją instalację osu! i doda skrót **osu! (taksiegra)** na pulpicie i w menu Start. Nie potrzeba uprawnień administratora, a twój zwykły skrót do osu! zostaje bez zmian.

Jeśli osu! jest zainstalowane w nietypowym miejscu, skrypt poprosi o wskazanie pliku `osu!.exe`.

**Windows (ręcznie)**

Kliknij prawym przyciskiem skrót do osu!, otwórz Właściwości i dopisz flagę na końcu pola Element docelowy, za cudzysłowem zamykającym:
```
"C:\Users\<ty>\AppData\Local\osu!\osu!.exe" -devserver taksiegra.ovh
```

**Linux (osu-winello)**

Uruchom to polecenie, aby automatycznie utworzyć skrót w menu aplikacji:
```bash
bash <(curl -fsSL https://taksiegra.ovh/downloads/setuptaksiegra.sh)
```
> Wymaga osu! zainstalowanego przez [osu-winello](https://github.com/NelloKudo/osu-winello) w domyślnej lokalizacji.

**Linux (inne konfiguracje Wine, Lutris)**

Dodaj `-devserver taksiegra.ovh` do argumentów uruchamiania `osu!.exe`.

### 3. Zaloguj się
Użyj nazwy i hasła podanych przy rejestracji. Po pierwszym udanym logowaniu
status konta automatycznie zmieni się z **Pending** na aktywny.

---

# Warto wiedzieć

- **osu!direct jest darmowy** dla wszystkich na taksiegra, bez supportera.
- **Powrót do oficjalnego osu!**: uruchom grę zwykłym skrótem. osu! pamięta jeden zapisany login dla obu serwerów, więc jeśli hasła się różnią, trzeba będzie wpisać właściwe jeszcze raz.
- **IRC**: klucz IRC znajdziesz na stronie w Ustawieniach, w zakładce Logowanie.

# Rozwiązywanie problemów

- **„Incorrect credentials”**: upewnij się, że najpierw zarejestrowałeś się na stronie i logujesz się kontem taksiegra, a nie kontem z oficjalnego osu!.
- **Gra łączy się z oficjalnym osu!**: brakuje flagi albo jest literówka. Flaga musi być poza cudzysłowem: `"...\osu!.exe" -devserver taksiegra.ovh`.
- **Polecenie w PowerShell nic nie robi albo pokazuje błąd**: użyj ręcznej metody opisanej wyżej.
- **Dalej nie działa?** Napisz do Python- (admin serwera). Jego Discord jest na [jego profilu](https://osu.taksiegra.ovh/u/3). Dołącz zrzut ekranu z błędem.

---

# Dodatkowe informacje

To prywatny serwer z ograniczonym, zaufanym gronem graczy. Jeśli administracja cię
nie kojarzy, możesz zostać zapytany, skąd wiesz o taksiegra. Konta, które odmówią
współpracy, mogą zostać ograniczone.

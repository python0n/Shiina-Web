placeholder
icon:fa-solid fa-server
route:connect
krz  :Connection
title:How to connect
navbar:true
footer:true
behindLogin:false

# How to connect to taksiegra

### 1. Create an account
Register at **https://osu.taksiegra.ovh**. Registering from inside the game is disabled.

### 2. Launch osu! with the devserver flag

You keep your normal osu! install, skins and beatmaps. The flag only changes which server the game connects to.

**Windows (automatic)**

Open PowerShell (press Win + X, then "Terminal" or "Windows PowerShell") and paste:
```
[Net.ServicePointManager]::SecurityProtocol = 'Tls12'; irm https://taksiegra.ovh/downloads/setuptaksiegra.ps1 | iex
```
This finds your osu! install and adds an **osu! (taksiegra)** shortcut to your Desktop and Start Menu. No administrator rights are needed and your normal osu! shortcut is not changed.

If osu! is installed somewhere unusual, the script asks you to pick `osu!.exe`.

**Windows (manual)**

Right-click your osu! shortcut, open Properties and add the flag at the end of the Target field, after the closing quote:
```
"C:\Users\<you>\AppData\Local\osu!\osu!.exe" -devserver taksiegra.ovh
```

**Linux (osu-winello)**

Run the following command to create a launcher shortcut automatically:
```bash
bash <(curl -s https://taksiegra.ovh/downloads/setuptaksiegra.sh)
```
> Requires osu! installed via [osu-winello](https://github.com/NelloKudo/osu-winello) in the default location.

**Linux (other Wine setups, Lutris)**

Add `-devserver taksiegra.ovh` to the launch arguments of `osu!.exe`.

### 3. Log in
Use the username and password you registered with. On your first successful login,
your account status will automatically change from **Pending** to active.

---

# Good to know

- **osu!direct is free** for everyone on taksiegra, no supporter tag needed.
- **Going back to official osu!**: start the game with your normal shortcut. osu! remembers one saved login for both servers, so if your passwords differ you will have to type the right one again.
- **IRC**: your IRC key is on the website under Settings, Authentication.

# Troubleshooting

- **"Incorrect credentials"**: make sure you registered on the website first and that you are logging in with your taksiegra account, not your official osu! account.
- **The game connects to official osu!**: the flag is missing or misspelled. It has to be outside the quotes: `"...\osu!.exe" -devserver taksiegra.ovh`.
- **The PowerShell command does nothing or shows an error**: use the manual method above, or ask on Discord.
- **Still stuck?** Ask the staff on Discord and include a screenshot of the error.

---

# Additional information

This is a private server with a limited, trusted player base. If the staff doesn't
recognize you, you may be asked how you heard about taksiegra. Accounts that refuse
to cooperate may be restricted.

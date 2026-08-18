# Survivor.io — Naval Monitor (Android Native App)

Aplicativo Android Nativo em **Kotlin** que atua como **Overlay (Janela Flutuante)** em tempo real sobre o jogo Survivor.io, decodificando pacotes do evento Batalha Naval transmitidos pelo **PCAPdroid**.

---

## 🚀 Como Compilar o Projeto no Android Studio

1. Abra o **Android Studio**.
2. Clique em **Open** e selecione a pasta `android_naval_monitor`.
3. Aguarde o Gradle sincronizar as dependências automaticamente.
4. Conecte seu dispositivo Android (com depuração USB ativa) ou inicie um Emulador.
5. Clique no botão **Run 'app'** (SHIFT + F10) para compilar e instalar o `.apk`.

---

## 📱 Como Usar no Celular

### Passo 1: Configurar o PCAPdroid
1. Baixe o **PCAPdroid** (na Google Play Store ou GitHub).
2. Baixe e ative o **PCAPdroid-mitm/TLS Decryption**.
3. Copie `pcapdroid_addon/naval_live_addon.py` para a pasta de addons do PCAPdroid-mitm.
4. Habilite o addon e conceda acesso a arquivos quando solicitado.
5. Crie a regra de descriptografia para o Survivor.io (`com.dxx.firenow`).
6. Configure o bloqueio de QUIC e permita o controle por API.
7. O addon envia JSON para `127.0.0.1:8086`; não é necessário usar o proxy nativo.

### Passo 2: Iniciar o Monitor
1. Abra o **Survivor Naval Monitor**.
2. Clique em **Conceder Permissão** para permitir a Janela Flutuante (`Draw over other apps`).
3. Clique em **INICIAR MONITOR**. O app inicia o PCAPdroid pela API e começa a escutar o addon.
4. A janelinha flutuante do tabuleiro naval aparecerá no topo da tela.

### Passo 3: Jogar!
1. Abra o **Survivor.io** e entre no evento naval.
2. O tabuleiro atualizará automaticamente revelando peças e tiros em tempo real!

---

## 🧪 Como Testar no PC sem Celular (Simulador Python)

Você também pode testar o aplicativo ou a transmissão usando o simulador Python incluído na raiz do projeto:

```bash
python simulate_pcapdroid_stream.py --ip <IP_DO_CELULAR> --port 8086
```
Este script lê as capturas `.mitm` reais do projeto e as transmite via UDP diretamente para o seu celular ou emulador Android!

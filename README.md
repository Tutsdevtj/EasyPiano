# EasyPiano

Piano Android nativo em Kotlin/Jetpack Compose, com sampler C++ e Oboe. Teclado **F1 (MIDI 29) a C8 (108)**: 80 teclas, **47 brancas e 33 pretas**. Afinação temperada, **A4 = 440 Hz**. Android 8.0+ (API 26); compile API 37.2 / target API 37 (Android 17).

## Usar no celular

1. Baixe `app-release.apk` na seção **Releases** do repositório, ou em **Actions → Android APK → Artifacts** (descompacte o ZIP do artifact). O APK também pode ser entregue diretamente.
2. Copie o arquivo para o celular e abra pelo gerenciador de arquivos.
3. Se o Android pedir, permita **Instalar apps desconhecidos** para o navegador ou gerenciador que está abrindo o arquivo.
4. Confirme a instalação e abra **EasyPiano**. A primeira abertura decodifica os samples com uma barra de progresso. Não precisa de internet.

Por USB, com depuração ativada:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

Atualizações exigem a **mesma chave de assinatura** e um `versionCode` igual ou maior. Um APK assinado com outra chave exige desinstalar o anterior (apagando os ajustes locais). Prefira fones **com fio**: Bluetooth adiciona atraso que o app não pode remover.

## Tocar e navegar

- Toque com vários dedos; cada ponteiro tem sua própria voz. Arrastar entre teclas faz glissando; sair da área solta a tecla. As pretas têm prioridade no hit test.
- O teclado de execução não rola. Use o **minimapa** para arrastar a janela ou tocar em uma região. Ao soltar, o movimento tem inércia.
- **ZOOM** mostra de 7 a 47 teclas brancas. **◀ / ▶** movem sete brancas (uma oitava); **‹ / ›**, uma branca. **Travar** bloqueia navegação e zoom.
- Em **Ajustes**, selecione rótulos, notação, acidentes e encaixe da navegação em Dó ou Fá.
- Segure **SUSTAIN**; ou ative alternância nos ajustes. A posição vertical do toque controla a intensidade, se ativada. Volume e todos os ajustes são persistidos com DataStore.
- O visor mostra a última nota disparada e sua frequência ideal. Em **Sobre**, veja os créditos e a configuração de saída negociada pelo Oboe.

Em tablets onde o Android 17 ignora a orientação solicitada pela Activity, um contêiner mantém o instrumento na horizontal dentro da janela. O recorte da câmera é respeitado antes dessa transformação.

## Gerar o APK localmente

Requisitos: **JDK 17 completo**, Android SDK Platform **37.2**, Build Tools **37.0.0**, NDK **27.2.12479018**, CMake **3.22.1**. O Gradle Wrapper baixa o Gradle **8.13**. Dependências e versões estão em `gradle/libs.versions.toml`.

No Linux, o setup instala SDK/NDK e grava `local.properties` (fora do Git):

```sh
export JAVA_HOME=/caminho/para/jdk-17
export ANDROID_HOME="$HOME/Android/Sdk"
bash scripts/setup-android.sh
./gradlew test assembleRelease
```

No Windows, instale os mesmos componentes pelo SDK Manager do Android Studio, configure `JAVA_HOME`/`ANDROID_HOME` e execute:

```powershell
.\gradlew.bat test assembleRelease
```

Resultado **assinado** (universal ARM64 + ARMv7):

```text
app/build/outputs/apk/release/app-release.apk
```

Não é preciso converter samples ou baixar arquivos de áudio para compilar: os 81 OGG já acompanham o projeto. Não há `MediaPlayer` ou `SoundPool`.

## Assinatura permanente

```sh
bash scripts/create-keystore.sh /caminho/seguro/easypiano-release.jks easypiano
```

Windows:

```powershell
.\scripts\create-keystore.ps1 -Path C:\Chaves\easypiano-release.jks -Alias easypiano
```

O `keytool` pede senhas interativamente. O script não sobrescreve uma chave existente. Guarde backup da chave e das senhas.

Crie `keystore.properties` na raiz (ignorado pelo Git):

```properties
storeFile=/caminho/seguro/easypiano-release.jks
storePassword=SUA_SENHA
keyAlias=easypiano
keyPassword=SUA_SENHA_DA_CHAVE
```

Ou use `PIANO_KEYSTORE_PATH`, `PIANO_KEYSTORE_PASSWORD`, `PIANO_KEY_ALIAS`, `PIANO_KEY_PASSWORD`. Variáveis de ambiente têm prioridade. **Sem configuração, o release usa a chave debug e emite aviso**, para continuar instalável. Configuração parcial falha explicitamente. Chaves debug de máquinas/runners diferentes podem ser diferentes: configure uma chave permanente para atualizar sem desinstalar.

## GitHub Actions e versões

`.github/workflows/release.yml` configura JDK 17/SDK/NDK, verifica hashes e cobertura dos samples, testa o sampler C++, roda `test assembleRelease lintRelease`, verifica a assinatura e envia o APK + SHA256 como artifact. Executa em push na `main`, PR, tag `v*` e manualmente.

Para assinar no CI, configure em **Settings → Secrets and variables → Actions**:

| Secret | Conteúdo |
| --- | --- |
| `KEYSTORE_BASE64` | Bytes do `.jks` em base64 (sem quebras de linha) |
| `KEYSTORE_PASSWORD` | Senha do keystore |
| `KEY_ALIAS` | Alias usado no keytool |
| `KEY_PASSWORD` | Senha da chave |

Linux: `base64 -w0 /caminho/seguro/easypiano-release.jks`.
PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\Chaves\easypiano-release.jks'))`.
Não publique o resultado em commits, issues ou logs.

Para lançar:

```sh
git tag v1.0.0
git push origin main
git push origin --tags
```

A tag cria um **GitHub Release com `app-release.apk` e SHA256 anexados**. Actions precisa estar habilitado e permitir ao workflow escrever em Contents. Sem Secrets, há aviso e assinatura debug. Em PR, Secrets não são usados.

`versionName = MAJOR.MINOR.PATCH`; `versionCode = MAJOR × 1.000.000 + MINOR × 1.000 + PATCH`. Minor/patch vão de 0 a 999, major até 2000. Tags devem ser `vMAJOR.MINOR.PATCH`. O workflow usa a tag mais recente também nos pushes normais; builds locais sem `VERSION_TAG` usam 0.1.0. Para reproduzir uma versão localmente:

```sh
VERSION_TAG=v1.0.0 ./gradlew test assembleRelease
```

## Áudio e samples

**Salamander Grand Piano V3**, gravações de um **Yamaha C5** por **Alexander Holm**, sob **Creative Commons Attribution 3.0 Unported**:

- Original: https://archive.org/details/SalamanderGrandPianoV3
- Projeto/documentação: https://github.com/sfzinstruments/SalamanderGrandPiano
- Distribuição OGG utilizada: https://github.com/darosh/samples-piano, pacotes npm `@audio-samples/piano-velocity4`, `piano-velocity9` e `piano-velocity15`, versão **1.0.5**.
- Licença dos samples: https://creativecommons.org/licenses/by/3.0/

Modificações: seleção de 27 raízes a cada três semitons (**F#1, A1, C2… C8**), três camadas v4/v9/v15; medição de fundamental e correção de afinação em runtime. **Os bytes OGG e as caudas completas são preservados**. F1 usa F#1 transposto um semitom para baixo; as demais teclas ficam a no máximo um semitom da raiz. O manifesto inclui hashes SHA256, frequências medidas, correções e duração. Os créditos também estão na tela **Sobre**.

O código MIT do empacotamento npm não muda a licença CC BY dos áudios. Textos de licença estão em `licenses/`. Decoder **stb_vorbis**, Sean Barrett (MIT/domínio público, licença no próprio arquivo). **Oboe**, Google (Apache 2.0).

Para reproduzir o subconjunto e a calibração (opcional; precisa ffmpeg/numpy/scipy):

```sh
python3 -m pip install numpy scipy
python3 scripts/prepare-samples.py
python3 scripts/verify-samples.py
```

A calibração usa picos interpolados da fundamental em três janelas por sample, mediana e uma razão de resampling contra a frequência ideal. Para agudos, as janelas ficam no início audível da nota, evitando medir ruído da cauda. Um piano acústico tem inarmonicidade e pequenas variações ao longo da cauda; a correção alinha a fundamental medida, sem transformar as gravações em ondas sintéticas. Verifique no afinador no aparelho antes de usar como referência de afinação.

### Motor

- 64 vozes principais + pool prealocado de caudas para o fade de 5 ms no roubo da voz mais antiga.
- Decodificação OGG em thread de IO antes de abrir a saída; PCM estéreo de 16 bits em memória (~198 MB para o banco, além da interface/decoder). Não há IO, mutex ou alocação no callback.
- Interpolação linear, afinação por razão de frequências, ataque de 2 ms, release exponencial de 220–460 ms, fade de final de sample e limitador suave.
- Eventos do thread principal via fila SPSC lock-free, 2048 posições; consumo limitado por callback. Overflow aciona panic com fade, evitando notas presas.
- Oboe `LowLatency`, `Exclusive`, Float estéreo e dois bursts; fallback Shared se Exclusive não estiver disponível. A frequência de saída é negociada com o aparelho.
- Pausa, perda de foco de áudio e saída do app encerram as vozes. Desconexão de fone/rota é detectada; a saída é reaberta sem fazer trabalho pesado no callback.
- R8 ativo e regras de preservação de JNI. Bibliotecas nativas alinhadas para páginas de 16 KB.

## Validação

```sh
./gradlew test assembleRelease lintRelease
python3 scripts/verify-samples.py
g++ -std=c++17 -O2 tests/sampler_test.cpp -o /tmp/sampler-test
/tmp/sampler-test
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
```

JUnit verifica extensão e ordem exatas, 47/33 teclas, frequências de todas as notas, rótulos, prioridade das pretas e limites de navegação. O teste nativo verifica sustain, release, razão de afinação, polifonia, roubo e overflow.

Um teste offline renderiza F1/A4/C8 pelo sampler nativo e mede as três camadas (`python3 scripts/test-rendered-tuning.py`, com numpy/scipy e compilador C++). Com `--all`, verifica as 80 notas nas três intensidades: os 240 renders passaram, com desvio máximo medido de 1,752 cents nas janelas de análise.

Para uma build de depuração x86_64 em emulador: `./gradlew -Pemulator=true assembleDebug`. A build normal de release continua universal ARM64/ARMv7.

Validação no aparelho ainda necessária: instalar em Android 8+, tocar acordes de cinco dedos, fazer glissando, conferir F1/A4/C8 num afinador, testar saídas de áudio/reconexão, minimapa/zoom/trava, persistência e notch. Latência física, ausência audível de estalos e 60 fps dependem da medição em hardware real; testes de compilação não os comprovam.

Extras opcionais (duas faixas, MIDI USB, reverb, pinça e háptica) não estão implementados.

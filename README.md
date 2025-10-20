 ## Sobre o projeto

O VisionWay é um aplicativo mobile desenvolvido como Trabalho de Conclusão de Curso (TCC), com o objetivo de auxiliar pessoas com deficiência visual em seus trajetos diários.
O aplicativo realiza detecção de objetos em tempo real utilizando Inteligência Artificial integrada ao TensorFlow Lite (TFLite).

O VisionWay é um aplicativo de detecção de objetos com IA;

## Funcionalidades do aplicativo:

- Captura de imagens em tempo real pela câmera do celular
- Detecção de objetos usando modelo treinado com YOLOv8n + TensorFlow Lite
- Feedback sonoro com nome do objeto detectado
- Interface acessível com botões de alto contraste

## Estrutura de pastas 

Inteligencia Artificial:

# Necessario utilizar o python 3.10 ou anterior

/Treinamento_IA                 # Códigos de treinamento da IA (não integrados diretamente ao app)
│
├── /dataset/                   # Dataset de imagens
│   ├── /train/                 # Imagens de treinamento
│   ├── /test/                  # Imagens de teste
│   └── /val/                   # Imagens de validação
│
├── /runs/                      # Resultados dos treinamentos
├── Trainning_model.py          # Script de treinamento do modelo YOLO
├── conversion_model.py         # Conversão do modelo YOLO para TensorFlow Lite
└── data.yaml                   # Configurações e labels do dataset



Aplicativo:

/app/src/main/
│
├── AndroidManifest.xml         # Declara todas as telas e permissões do app
├── MenuActivity.kt             # Tela inicial (menu principal)
├── AppActivity.kt              # Tela principal de detecção
├── ConfigActivity.kt           # Tela de configurações (seleção de voz)
├── VoicePrefs.kt               # Preferências do usuário (voz, gênero)
├── TtsUtils.kt                 # Funções para interagir com o Google TTS
├── CustomTabsHelper.kt         # Abre navegador com link do botão de suporte
└── /res/layout/                # Arquivos XML de layout e estilo do app


## Tecnologias utilizadas

Back-end / IA:

Python
Ultralytics YOLO
PyTorch
TensorFlow Lite (TFLite)

Mobile (Android):

Kotlin
Java 8
Android SDK (API 35 - Android 15)
Camera2 API
Android Jetpack (AndroidX)
Chrome Custom Tabs

## Get Started

Treinamento IA;

Crie um ambiente virtual em sua IDE (Python 3.10 ou anterior).

Acesse o site oficial do PyTorch e copie o comando de instalação compatível com sua GPU (NVIDIA, se disponível).

Após instalar o PyTorch, execute os seguintes comandos no console:

pip install ultralytics          # Instalação do YOLO
pip install tf_keras             # Instalação do TensorFlow
pip install onnx2tf==1.24.0      # Instalação do conversor ONNX -> TFLite
pip install onnx_graphsurgeon    # Ferramenta auxiliar do onnx2tf
pip install sng4onnx             # Otimizador "Simplifying ONNX Graphs"


Aplicativo Android;

Abra o android studio e clone o repositório:

https://github.com/LucasSilva3101/TCC_VisionWay.git

Após clonar aguarde o Gradle sincronizar;

Conecte um dispositivo Android físico ou use um emulador com câmera habilitada;

Clique em Run App;

O aplicativo será instalado e executado automaticamente.
Durante o uso, o app solicitará permissões para usar a câmera e o áudio (TTS) — aceite para o funcionamento correto.

Se a detecção não funcionar, verifique se o arquivo
best_float32.tflite está realmente dentro de:

app/src/main/assets/

E se o labels.txt corresponde às classes usadas no dataset.



Este projeto foi desenvolvido como Trabalho de Conclusão de Curso (TCC) por alunos do curso de [Ciência da Computação] da [Universidade Paulista UNIP].

Este projeto é de código aberto e foi desenvolvido exclusivamente para fins acadêmicos.

Você pode:

Visualizar e estudar o código-fonte
Utilizar partes do código para fins educacionais ou de pesquisa
Mas não é permitido:
Comercializar o aplicativo ou o código
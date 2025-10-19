from ultralytics import YOLO

def main():

    model = YOLO("yolov8n.pt") # Carregar o modelo para treinamento da IA


    model.train(
        data="data.yaml",          # arquivo do onde contém as labels marcadas do dataset
        epochs=50,                 # Epocas para apreendizado
        imgsz=640,                 # Tamanho da imagem
        batch=8,                   # Caso trave diminua para 4 ou 2
        device=0,                  # Seleciona a GPU (instale o pytorch
        half=True,                 # economizar VRAM
        name="best",               # Nome do arquivo
        patience=25,               # caso a IA pare de aprender ela para na epoca 25
        verbose=True               # logs
    )

    # Prova que o modelo aprendeu com o treinamento
    metrics = model.val()
    print("Métricas de validação:", metrics)

    # Previsões ou exportações opcionais
    # results = model("https://ultralytics.com/images/bus.jpg")  # Fazer predição em uma imagem de teste



if __name__ == "__main__":
    # freeze_support()  # Descomente se usar multiprocessing no Windows
    main()

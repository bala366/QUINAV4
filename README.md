# Quina Posicional V4

Projeto Android reconstruído do zero para evitar o crash de inicialização da versão anterior.

## Identidade visual
- Fundo branco.
- Trevo roxo com detalhe branco no ícone do Android.
- Cabeçalho roxo e título branco `☘ QUINA POSICIONAL`.

## Estudo
- P01 a P05 usando o histórico inteiro.
- Frequência por posição e força recente.
- Pareto posicional para reduzir o universo antes do cálculo pesado.
- Engrossamento do talo e arrasto sem reset.
- Dois últimos ciclos completos.
- Padrões próprios da Quina: soma, ímpares, primos e amplitude.
- Cálculo limitado a no máximo 50.000 candidatos após a redução posicional para não congelar o celular.

## Estabilidade
- A tela abre sem ler arquivo ou acessar armazenamento.
- O histórico é escolhido pelo seletor oficial do Android (`ACTION_OPEN_DOCUMENT`).
- A análise roda em `ExecutorService`, fora da thread da interface.
- O progresso é atualizado durante todas as etapas pesadas.
- Exceções são capturadas e mostradas na tela em vez de fechar o aplicativo.
- O botão PDF só é habilitado após uma análise concluída.

## PDF
- Volante 01 a 80.
- Verde = 5 dezenas sugeridas.
- Vermelho = falhas.
- Resumo do motor e P01-P05.
- Android 10+ salva em `Downloads/Quina Posicional` via MediaStore.

## Formato do TXT
Cada linha precisa conter pelo menos as 5 dezenas do concurso, entre 01 e 80. Pode conter também o número do concurso antes delas. O app usa as últimas 5 dezenas válidas da linha.

## GitHub Actions
Workflow já incluído em `.github/workflows/build-apk.yml`.
O workflow falha se o APK não for realmente criado e só publica o artefato após validar `app/build/outputs/apk/debug/app-debug.apk`.

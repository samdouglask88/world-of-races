# Realmfolk

Realmfolk é um mod para Minecraft focado em habitantes humanos integrados ao mundo. O projeto reúne famílias, relações sociais, profissões, comércio, economia, povoados, construções autônomas e liderança medieval.

Millénaire, MineColonies e Minecraft Comes Alive são referências conceituais para a experiência. Realmfolk possui implementação, código e recursos próprios.

## Estado do projeto

Em desenvolvimento. A base atual inclui habitantes humanos, identidade persistente, famílias, casas, profissões, inventário, equipamentos, comércio, diálogos e comandos de movimento.

## Requisitos

- Minecraft 1.20.1
- Forge 47.4.10 ou superior
- Java 17

## Como compilar e executar

No Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

## Compatibilidade de mundos antigos

A mudança do identificador `worldofraces` para `realmfolk` altera os IDs registrados, os namespaces de recursos e os nomes dos dados salvos. Mundos criados com versões anteriores podem perder referências, apresentar itens ou entidades ausentes e deixar de carregar dados do mod corretamente. Faça backup antes de abrir um mundo antigo com Realmfolk.

## Licença

Todos os direitos reservados. Consulte o arquivo `LICENSE` para detalhes sobre uso e redistribuição.

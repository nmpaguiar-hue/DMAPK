# DM Sync 0.3.0

Aplicação administrativa Android (protótipo de testes).

## Fluxo

1. No separador Portais, abrir a gestão de membros dos Dragões Marcoenses e iniciar sessão.
2. Premir «Importar lista». A aplicação tenta percorrer as páginas da listagem e deduplicar os registos pelo número de sócio da Casa.
3. Quando for necessário o número de sócio FC Porto, abrir a ficha individual no portal e escolher «Ler ficha». Confirmar o documento de identificação, o email e a data de admissão na app.
4. Aprovar individualmente cada registo na app.
5. Na ficha aprovada, premir «Adicionar ao Portal». A app abre a Comunidade FC Porto, tenta escolher Sócio ou Adepto, preencher a pesquisa de identificação e preparar os campos da comunidade.
6. Confirmar manualmente e premir «Adicionar» no portal do FC Porto para concluir a submissão. Não existe envio final sem ação explícita do administrador.

## Limitações

* As interfaces dos dois portais são externas e podem mudar. A importação, passagem de páginas e preenchimento assistido necessitam de testes reais num dispositivo autenticado.
* A app não usa nem guarda passwords dos portais.
* A app não executa tarefas em segundo plano nem deteta alterações sem abertura/importação.
* Dados de sócios são privados, cifrados no Android Keystore, guardados apenas no dispositivo.
* Não confundir a contagem global de sócios com novos membros. A deteção real usa números de sócio para evitar duplicação.
* Não usar este protótipo para registos reais antes de verificar o funcionamento do formulário e os direitos de acesso.

Build: GitHub Actions no ramo dm-sync-prototype. Este ramo não deve ser integrado na aplicação INVICTA.

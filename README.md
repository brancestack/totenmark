# Totenmark

Backend de marketplace em Clojure, Pedestal e SQLite.

## Configuração

O segredo JWT é obrigatório. As demais variáveis possuem valores padrão para desenvolvimento.

```powershell
$env:TOTENMARK_JWT_SECRET="use-um-segredo-longo-e-aleatorio"
$env:TOTENMARK_JWT_TTL_SECONDS="3600"
$env:TOTENMARK_PORT="8890"
$env:TOTENMARK_DB_NAME="totenmark"
```

Não versione o segredo JWT. Use um valor persistente no gerenciador de segredos do ambiente de produção.

## Banco de dados

Inicie o REPL e aplique as migrations:

```bash
lein repl
```

```clojure
(require '[totenmark.db.migration :as migration])
(migration/migrate!)
```

A migration `003-product-owner` adiciona o proprietário aos produtos. Produtos criados antes dela ficam sem proprietário e devem ser atribuídos ou recriados antes de poderem ser alterados pela API.

## Executar

```bash
lein run
```

## Autenticação

Cadastre um usuário:

```bash
curl -X POST -H "Content-Type: application/json" -d '{"username":"Alice","email":"alice@example.com","password":"password123"}' http://localhost:8890/api/user/create
```

Faça login:

```bash
curl -X POST -H "Content-Type: application/json" -d '{"email":"alice@example.com","password":"password123"}' http://localhost:8890/api/auth/login
```

Envie o token retornado nas demais rotas:

```bash
curl -H "Authorization: Bearer SEU_TOKEN" http://localhost:8890/api/product/all
```

O cadastro e o login são públicos. Todas as outras rotas exigem autenticação. Cada usuário só pode atualizar ou excluir a própria conta e os próprios produtos.

## Rotas

### Usuários

- `POST /api/user/create`
- `GET /api/user/all`
- `GET /api/user/:id`
- `PATCH /api/user/update`
- `DELETE /api/user/delete`

### Produtos

- `POST /api/product/create`
- `GET /api/product/all`
- `GET /api/product/:id`
- `PATCH /api/product/update`
- `DELETE /api/product/delete`

As listagens aceitam paginação com `limit` (1 a 100, padrão 50) e `offset`. Use `q` para busca textual. A listagem de produtos também aceita `category=donation|sale` e `status=available|reserved`.

```bash
curl -H "Authorization: Bearer SEU_TOKEN" "http://localhost:8890/api/product/all?limit=20&offset=0&category=sale&q=bike"
```

## Testes

```bash
lein test
```

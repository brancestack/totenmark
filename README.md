# Totenmark

Totenmark é um pequeno marketplace para vender ou doar coisas que ainda têm vida útil. O projeto também é um espaço para explorar Clojure em um domínio real, com autenticação, catálogo, reservas e as inevitáveis regras de quem pode mexer em quê.

No momento, a intenção é manter um monólito simples e bem cuidado. Pedestal recebe as requisições, HoneySQL e `next.jdbc` cuidam do banco, e o SQLite segura o MVP sem pedir infraestrutura extra.

## O que já funciona

- cadastro, login e logout com JWT; trocar a senha encerra todas as sessões, inclusive a atual;
- catálogo público com busca, filtros e paginação;
- anúncios de venda ou doação, com até oito imagens por URL;
- edição e exclusão restritas ao dono do anúncio;
- reserva com expiração e proteção contra duas pessoas reservarem ao mesmo tempo;
- CORS por ambiente, rate limiting no cadastro/login e logs com `X-Request-ID`;
- health checks e testes de integração usando um banco temporário de verdade.

## Rodando localmente

Você vai precisar de Java 21 e Leiningen.

O projeto não lê `.env` sozinho. Use o [.env.example](.env.example) como referência e exporte as variáveis no terminal. No PowerShell, o mínimo é:

```powershell
$env:TOTENMARK_JWT_SECRET="troque-isto-por-um-segredo-longo"
```

As outras configurações já têm valores razoáveis para desenvolvimento:

| Variável | Padrão | Para que serve |
| --- | --- | --- |
| `TOTENMARK_PORT` | `8890` | porta HTTP |
| `TOTENMARK_DB_NAME` | `totenmark` | arquivo do SQLite |
| `TOTENMARK_JWT_TTL_SECONDS` | `3600` | duração do token |
| `TOTENMARK_RESERVATION_TTL_SECONDS` | `86400` | duração da reserva |
| `TOTENMARK_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | frontends autorizados pelo CORS |

Porta e tempos de expiração são conferidos na inicialização. Um valor inválido interrompe o processo com o nome da configuração que precisa ser corrigida.

Aplique as migrations no REPL:

```bash
lein repl
```

```clojure
(require '[totenmark.db.migration :as migration])
(migration/migrate!)
```

Depois, suba a API:

```bash
lein run
```

Ela estará em `http://localhost:8890`.

### Sobre a migration 004

Todo anúncio agora precisa ter um dono. Por segurança, a migration não apaga anúncios antigos sem `user_id`: ela interrompe antes de reconstruir a tabela e preserva os dados. Antes de aplicá-la em um banco que já tenha dados, confira:

```sql
SELECT product_id, product_name
FROM products
WHERE user_id IS NULL;
```

Associe esses anúncios a uma conta ou remova-os conscientemente. Faça backup do arquivo SQLite antes de migrar um banco importante.

A migration 006 normaliza os e-mails antigos. Se duas contas tiverem o mesmo endereço com diferenças apenas de maiúsculas ou espaços, ela também interrompe sem alterar os usuários. Este diagnóstico mostra os conflitos:

```sql
SELECT lower(trim(email)) AS email, COUNT(*) AS total
FROM users
GROUP BY lower(trim(email))
HAVING COUNT(*) > 1;
```

## Estruturas de API

Crie uma conta:

```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"username":"Alice","email":"alice@example.com","password":"password123"}' \
  http://localhost:8890/api/users
```

Entre e guarde o token retornado:

```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}' \
  http://localhost:8890/api/auth/login
```

Publique um anúncio:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{"product-name":"Bicicleta","description":"Pronta para rodar","price":500,"category":"sale","image-urls":["https://example.com/bike.jpg"]}' \
  http://localhost:8890/api/products
```

O preço é armazenado como inteiro. A aplicação cliente deve definir a unidade — por exemplo, centavos — e usá-la de forma consistente.

Senhas precisam ter pelo menos oito caracteres e caber nos 72 bytes aceitos pelo BCrypt. Esse segundo limite importa para senhas com emoji ou outros caracteres que ocupam mais de um byte.

O catálogo não exige login:

```bash
curl "http://localhost:8890/api/products?limit=20&category=sale&status=available&q=bicicleta"
```

Para reservar e cancelar uma reserva:

```bash
curl -X POST -H "Authorization: Bearer SEU_TOKEN" \
  http://localhost:8890/api/products/1/reservations

curl -X DELETE -H "Authorization: Bearer SEU_TOKEN" \
  http://localhost:8890/api/products/1/reservations
```

## Rotas atuais

| Método | Caminho | Acesso |
| --- | --- | --- |
| `POST` | `/api/users` | público |
| `GET` | `/api/users` | autenticado |
| `GET` | `/api/users/:id` | autenticado |
| `PATCH` | `/api/users/:id` | própria conta |
| `DELETE` | `/api/users/:id` | própria conta |
| `POST` | `/api/auth/login` | público |
| `POST` | `/api/auth/logout` | autenticado |
| `GET` | `/api/products` | público |
| `GET` | `/api/products/:id` | público |
| `POST` | `/api/products` | autenticado |
| `PATCH` | `/api/products/:id` | dono do anúncio |
| `DELETE` | `/api/products/:id` | dono do anúncio |
| `POST` | `/api/products/:id/reservations` | autenticado |
| `DELETE` | `/api/products/:id/reservations` | comprador ou vendedor |

As rotas antigas em `/api/user/*` e `/api/product/*` ainda existem para não quebrar os primeiros exemplos do projeto. Código novo deve usar as rotas acima.

As listagens aceitam `limit` e `offset`; a de produtos também recebe `q`, `user-id`, `category` e `status`. A resposta inclui `total` e `has-more`. A busca por `q` ignora maiúsculas e minúsculas, mas trata `%` e `_` como texto comum, não como curingas do banco.

IDs devem ser inteiros positivos. JSON malformado recebe `400`; campos válidos, mas fora do contrato da rota, recebem `422`.

## Saúde e diagnóstico

- `GET /health` confirma que o processo está no ar;
- `GET /ready` confirma que o esquema essencial do banco foi migrado;
- cada resposta leva um `X-Request-ID`, também presente no log JSON da requisição.

## Testes

```bash
lein test
lein check
```

Há três tipos de teste no projeto:

- regras pequenas, como validação de senha, claims do JWT e limites de requisição;
- migrations em arquivos SQLite temporários, incluindo falha segura, correção dos dados, rollback e nova aplicação;
- fluxos HTTP pelo pipeline real do Pedestal, com cadastro, privacidade, catálogo, rotas antigas, expiração e exclusões em cascata.

As disputas por e-mail e por reserva também são executadas em paralelo. Há ainda uma troca simultânea entre quem cancela e quem tenta reservar em seguida; depois de cada rodada, o teste confere o estado do anúncio e a quantidade real de reservas no banco.

Os logs JSON exibidos durante `lein test` são as próprias requisições de integração. Eles ficam visíveis de propósito para que uma falha possa ser localizada pelo `request-id`.

## Limites conhecidos

O rate limiting vive na memória do processo, e o SQLite foi escolhido pensando em uma única instância. Se o Totenmark crescer para vários servidores, esses dois pontos devem migrar, respectivamente, para algo compartilhado (como Redis ou o gateway) e para PostgreSQL. As imagens ainda são URLs externas; upload e armazenamento ficam para uma etapa posterior.

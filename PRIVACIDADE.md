# Aviso de privacidade — Controle de Cheques

**Versão do aplicativo:** 1.15 (versão de testes)

## Dados tratados
O aplicativo permite inserir nomes de clientes, datas de vencimento, valores de cheques, histórico de operações e parâmetros de cálculo. Esses dados são armazenados localmente no aparelho. Nesta versão, o aplicativo não possui conta de usuário, não envia esses dados para servidores da ORVEX e não utiliza anúncios ou ferramentas de análise.

## Proteção e armazenamento
No Android, taxas e histórico são armazenados em preferências locais criptografadas com AES-GCM, usando uma chave mantida pelo Android Keystore. A proteção depende da segurança do próprio aparelho. Notificações de vencimento usam texto genérico para evitar exibir nomes e valores na tela bloqueada.

## Backup e compartilhamento
Quando o usuário escolhe uma pasta de backup ou exporta o texto manualmente, o arquivo de backup contém dados de operações e configurações em formato legível (JSON). O usuário deve guardá-lo em local privado e não compartilhá-lo publicamente. O compartilhamento de cálculos só ocorre quando iniciado pelo usuário.

## Permissões
O aplicativo pode solicitar permissão de notificações para avisos de vencimento e recebe o evento de inicialização do Android para reagendar lembretes. Não solicita localização, contatos, câmera, microfone ou acesso geral a arquivos.

## Exclusão
A opção **Taxas → Privacidade e dados → Apagar todos os dados do aplicativo** remove taxas, histórico, lembretes e a referência à pasta de backup configurada. Arquivos de backup exportados anteriormente precisam ser apagados pelo usuário no local onde foram salvos.

## Contato
Esta é uma minuta de privacidade para a versão de testes. Antes da publicação, a ORVEX deverá disponibilizar esta política em uma página pública HTTPS e inserir um canal de contato válido do desenvolvedor.

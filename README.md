# spring-gift-order

## 작업 배경
- OAuth와 JWT 기반 카카오 소셜 로그인 기능을 도입하여 카카오 API 서비스를 활용할 준비를 하고자 한다.


## 구현 범위
- 카카오 소셜 로그인 기능 구현
- client id가 리다이렉트 url에 노출되므로 client secret 활용


## 작업 방식
- 클라이언트 카카오 로그인 요청
- 서버에서 카카오 인증 서버로 리다이렉트 시키기
- 사용자가 로그인 진행 시, 카카오 인증 서버는 사용자를 미리 정해둔 서버의 특정 url로 리다이렉트 시킴(쿼리 파라미터에 인가 코드 포함)
- 서버는 해당 요청을 컨트롤러에서 핸들링해서 인가 코드를 얻어냄
- 얻어낸 인가 코드로 바로 카카오 인증 서버에 토큰 요청
- 클라이언트에게는 받은 엑세스 토큰과 리프레시 토큰을 응답


## 추가 구현 (여유되는 만큼)
- 멤버 도메인이 기존 로그인 시스템과 소셜 로그인을 통합하게끔 설계 변경
    - 비밀번호 nullable(소셜 로그인이 아니라면 필수로 전달받게끔 하기)
    - 엑세스 토큰과 리프레시 토큰 필드를 추가 (nullable)
- 서버에서 카카오 인증 서버로부터 토큰을 받고나면, 해당 토큰으로 카카오 인증 서버에 유저 정보 요청. 여기서 받은 유저 정보(회원번호, 이메일 받아오기)로 멤버 엔티티를 만들어 DB에 영속화
- 특정 API를 쓸 때 액세스 토큰을 받은 경우, 우선 인터셉터에서 JWT 검증하고, 카카오 인증 서버에서 토큰 검증 수행시키고 회원번호 받아서 DB에 있는 유저 정보 식별해냄으로써 검증 끝. API 응답

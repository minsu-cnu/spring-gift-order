package gift.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gift.product.dto.auth.AccessAndRefreshToken;
import gift.product.dto.auth.JwtResponse;
import gift.product.dto.auth.MemberDto;
import gift.product.exception.LoginFailedException;
import gift.product.model.Member;
import gift.product.property.KakaoProperties;
import gift.product.repository.AuthRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Transactional(readOnly = true)
public class AuthService {

    private final AuthRepository authRepository;

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    private final KakaoProperties kakaoProperties;

    private final RestClient restClient = RestClient.builder().build();
    ;

    private final String KAKAO_AUTH_CODE_BASE_URL = "https://kauth.kakao.com/oauth/authorize?scope=talk_message,account_email&response_type=code";

    public AuthService(AuthRepository authRepository, KakaoProperties kakaoProperties) {
        this.authRepository = authRepository;
        this.kakaoProperties = kakaoProperties;
    }

    @Transactional
    public void register(MemberDto memberDto) {
        validateMemberExist(memberDto);

        Member member = new Member(memberDto.email(), memberDto.password());
        authRepository.save(member);
    }

    public JwtResponse login(MemberDto memberDto) {
        validateMemberInfo(memberDto);

        Member member = authRepository.findByEmail(memberDto.email());

        String accessToken = getAccessToken(member);

        return new JwtResponse(accessToken);
    }

    public String getKakaoAuthCodeUrl() {
        return KAKAO_AUTH_CODE_BASE_URL + "&redirect_uri=" + kakaoProperties.redirectUrl()
            + "&client_id=" + kakaoProperties.clientId();
    }

    public AccessAndRefreshToken getAccessAndRefreshToken(String code, String externalApiUrl) {
        LinkedMultiValueMap<String, String> body = generateBodyForToken(code);

        ResponseEntity<String> response = restClient.post()
            .uri(URI.create(externalApiUrl))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, ((req, res) -> {
                throw new LoginFailedException("토큰 발급 관련 에러가 발생하였습니다. 다시 시도해주세요.");
            }))
            .toEntity(String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String accessToken = rootNode.path("access_token").asText();
            String refreshToken = rootNode.path("refresh_token").asText();

            return new AccessAndRefreshToken(accessToken, refreshToken);
        } catch (Exception e) {
            throw new LoginFailedException("소셜 로그인 진행 중 예기치 못한 오류가 발생하였습니다. 다시 시도해 주세요.");
        }
    }

    public void registerKakaoMember(String accessToken, String externalApiUrl) {
        ResponseEntity<String> response = restClient.post()
            .uri(URI.create(externalApiUrl))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, ((req, res) -> {
                throw new LoginFailedException("카카오 유저 정보 조회 관련 에러가 발생하였습니다. 다시 시도해주세요.");
            }))
            .toEntity(String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String memberEmail = rootNode.path("kakao_account").path("email").asText();

            if (!authRepository.existsByEmail(memberEmail)) {
                authRepository.save(new Member(memberEmail, "oauth"));
            }
        } catch (Exception e) {
            throw new LoginFailedException("소셜 로그인 진행 중 예기치 못한 오류가 발생하였습니다. 다시 시도해 주세요.");
        }
    }

    public long unlinkKakaoAccount(String accessToken, String externalApiUrl) {
        ResponseEntity<String> response = restClient.post()
            .uri(URI.create(externalApiUrl))
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, ((req, res) -> {
                throw new LoginFailedException("카카오 유저 연결을 끊는 도중 에러가 발생하였습니다. 다시 시도해주세요.");
            }))
            .toEntity(String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());

            return Long.parseLong(rootNode.path("id").asText());
        } catch (Exception e) {
            throw new LoginFailedException("소셜 로그인 진행 중 예기치 못한 오류가 발생하였습니다. 다시 시도해 주세요.");
        }
    }

    private String getAccessToken(Member member) {
        String EncodedSecretKey = Encoders.BASE64.encode(
            SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        byte[] keyBytes = Decoders.BASE64.decode(EncodedSecretKey);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
            .claim("id", member.getId())
            .signWith(key)
            .compact();
    }

    private void validateMemberExist(MemberDto memberDto) {
        boolean isMemberExist = authRepository.existsByEmail(memberDto.email());

        if (isMemberExist) {
            throw new IllegalArgumentException("이미 회원으로 등록된 이메일입니다.");
        }
    }

    private void validateMemberInfo(MemberDto memberDto) {
        boolean isMemberExist = authRepository.existsByEmail(memberDto.email());

        if (!isMemberExist) {
            throw new LoginFailedException("회원 정보가 존재하지 않습니다.");
        }

        Member member = authRepository.findByEmail(memberDto.email());

        if (!memberDto.password().equals(member.getPassword())) {
            throw new LoginFailedException("비밀번호가 일치하지 않습니다.");
        }
    }

    private LinkedMultiValueMap<String, String> generateBodyForToken(String code) {
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", kakaoProperties.grantType());
        body.add("client_id", kakaoProperties.clientId());
        body.add("redirect_url", kakaoProperties.redirectUrl());
        body.add("code", code);
        return body;
    }
}

# SHA-256 초기화 상수 (첫 8개 소수의 제곱근의 소수점 이하 첫 32비트)
H = [
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
]

# 라운드 상수 K (첫 64개 소수의 세제곱근의 소수점 이하 첫 32비트)
K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
]

# 32비트 오른쪽 순환 이동 (Rotate Right)
def RotateRight(x, n):
    return ((x >> n) | (x << (32 - n))) & 0xFFFFFFFF # 연산이 끝날 때마다 & 0xFFFFFFFF으로 32비트를 초과하는 상위 비트를 강제로 지움

# SHA-256에서 사용하는 공식들
def Sigma0(x): return RotateRight(x, 2) ^ RotateRight(x, 13) ^ RotateRight(x, 22)
def Sigma1(x): return RotateRight(x, 6) ^ RotateRight(x, 11) ^ RotateRight(x, 25)
def sigma0(x): return RotateRight(x, 7) ^ RotateRight(x, 18) ^ (x >> 3)
def sigma1(x): return RotateRight(x, 17) ^ RotateRight(x, 19) ^ (x >> 10)
def Choice(x, y, z): return (x & y) ^ (~x & z)
def Majority(x, y, z): return (x & y) ^ (x & z) ^ (y & z)

# TODO: 다시 처음부터 이해하면서 코딩
def Sha256(message: bytes) -> str:
    # 메시지 패딩
    bitLength = len(message) * 8
    # 메시지 뒤에 0x80 (이진수 10000000)을 붙임
    padding = bytearray(message)
    padding.append(0x80)
    # 메시지 길이가 512비트의 배수보다 64비트 부족할 때까지 0으로 채움
    while (len(padding) * 8) % 512 != 448:
        padding.append(0x00)
    # 마지막 64비트에 원본 메시지의 비트 길이를 8바이트 Big-endian(앞을 0으로 채움)으로 추가
    padding.extend(bitLength.to_bytes(8, byteorder='big'))

    # 512비트 블록 단위로 메시지를 처리
    for i in range(0, len(padding), 64):
        block = padding[i:i+64]

        # 메시지 스케줄링
        W = [0] * 64 # 64개의 워드로 초기화
        for t in range(16):
            W[t] = int.from_bytes(block[t*4:(t+1)*4], byteorder='big') # 4바이트씩 읽어와서 32비트 정수로 변환
            
        for t in range(16, 64):
            W[t] = (sigma1(W[t-2]) + W[t-7] + sigma0(W[t-15]) + W[t-16]) & 0xFFFFFFFF # 32비트로 제한

        # 압축 루프
        a, b, c, d, e, f, g, h = H # 초기 해시 값 설정
        for t in range(64):
            T1 = (h + Sigma1(e) + Choice(e, f, g) + K[t] + W[t]) & 0xFFFFFFFF
            T2 = (Sigma0(a) + Majority(a, b, c)) & 0xFFFFFFFF
            h = g
            g = f
            f = e
            e = (d + T1) & 0xFFFFFFFF
            d = c
            c = b
            b = a
            a = (T1 + T2) & 0xFFFFFFFF

        # 해시 값 업데이트
        H[0] = (H[0] + a) & 0xFFFFFFFF
        H[1] = (H[1] + b) & 0xFFFFFFFF
        H[2] = (H[2] + c) & 0xFFFFFFFF
        H[3] = (H[3] + d) & 0xFFFFFFFF
        H[4] = (H[4] + e) & 0xFFFFFFFF
        H[5] = (H[5] + f) & 0xFFFFFFFF
        H[6] = (H[6] + g) & 0xFFFFFFFF
        H[7] = (H[7] + h) & 0xFFFFFFFF

    # 최종 해시 값 생성
    hash_value = b''.join(h.to_bytes(4, byteorder='big') for h in H) # 32비트 정수를 바이트로 변환하고 이어붙임
    return hash_value.hex()



print(f"==================================================")
print(f"              SHA-256 해시 프로그램      ")
print(f"==================================================")

plaintext = input("암호화할 텍스트를 입력하세요: ")
encoded_plaintext = plaintext.encode('utf-8')  # 입력 문자열을 바이트로 인코딩d
print(f"입력값: {plaintext}")
print(f"결과값: {Sha256(encoded_plaintext)}")

print(f"==================================================")
print(f"                프로그램을 종료합니다.       ")
print(f"==================================================")

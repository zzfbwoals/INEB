import random

# 100 이하의 소수 리스트
primes = [
    2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 
    43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97
]

# 최대공약수 (유클리드 호제법)
def Gcd(a, b):
    while b != 0:
        a, b = b, a % b
    return a

# 최소공배수
def Lcm(a, b):
    return (a * b) // Gcd(a, b)

# 키 값 구하기
def GenerateKeypair():
    # 소수 리스트에서 랜덤으로 하나 추출
    p = random.choice(primes)
    q = random.choice(primes)
    while p == q:  # p와 q가 같지 않도록
        q = random.choice(primes)
    
    n = p * q
    # 자연수 n이하의 수 중 n과 서로소인 수의 개수
    phi = (p - 1) * (q - 1)

    # e 선택 (1 < e < phi, gcd(e, phi) == 1)
    # φ(n)과 서로소이고 φ(n)보다 작은 랜덤 값
    e = random.choice([x for x in range(2, phi) if Gcd(x, phi) == 1])

    # d 계산 (d * e ≡ 1 mod phi) = (d = e^(-1) mod φ(n))
    d = pow(e, -1, phi)

    print(f"\n--- 키 생성 과정 ---")
    print(f"1. 선택된 두 소수: p = {p}, q = {q}")
    print(f"2. n (p * q) 계산: n = {n}")
    print(f"3. 오일러 피 함수 값 φ(n) = (p-1)*(q-1): {phi}")
    print(f"4. 공개키 지수 e (φ(n)과 서로소): {e}")
    print(f"5. 개인키 지수 d (e * d ≡ 1 mod φ(n)): {d}")
    print(f"---------------------\n")

    return ((e, n), (d, n))  # 공개키와 개인키 반환

# 암호화: C = M^e mod n
def Encrypt(public_key, message_number):
    e, n = public_key
    ciphertext = pow(message_number, e, n)
    return ciphertext

# 복호화: M = C^d mod n
def Decrypt(private_key, ciphertext_number):
    d, n = private_key
    plaintext = pow(ciphertext_number, d, n)
    return plaintext

while True:
    print(f"==================================================")
    print(f"              RSA 암호화/복호화 프로그램     ")
    print(f"==================================================")

    print(f"작업을 선택하세요 (1: 암호화, 2: 복호화, 0: 종료): ", end="")
    choice = input().strip()

    if choice == '0':
        print(f"\n==================================================")
        print(f"                프로그램을 종료합니다.       ")
        print(f"==================================================")
        break

    elif choice == '1':
        public_key, private_key = GenerateKeypair()
        print(f"공개키 (e, n): {public_key}")
        print(f"개인키 (d, n): {private_key}")
        try:
            message = int(input(f"암호화할 숫자를 입력하세요 (0 ~ {public_key[1]-1} 권장): "))
            ciphertext = Encrypt(public_key, message)
            print(f"암호화 결과: {ciphertext}")
        except ValueError:
            print("숫자만 입력해 주세요")

    elif choice == '2':
        try:
            private_key_input = input("개인키를 입력하세요 (d, n): ")
            d, n = map(int, private_key_input.strip("()").split(","))
            private_key = (d, n)
            ciphertext = int(input("복호화할 암호문(숫자)을 입력하세요: "))
            plaintext = Decrypt(private_key, ciphertext)
            print(f"복호화 결과: {plaintext}\n\n")
        except ValueError:
            print("숫자만 입력해 주세요\n\n")

    else:
        print("올바른 선택을 해주세요\n\n")
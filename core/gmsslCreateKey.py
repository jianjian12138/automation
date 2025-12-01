# 导入系统随机数生成器，用于生成安全的随机私钥
from random import SystemRandom


# 椭圆曲线参数类：定义有限域上的椭圆曲线方程 y² = x³ + A x + B (mod P)
class CurveFp:
    def __init__(self, A, B, P, N, Gx, Gy, name):
        self.A = A          # 曲线参数A
        self.B = B          # 曲线参数B
        self.P = P          # 有限域素数
        self.N = N          # 曲线阶（生成元的阶）
        self.Gx = Gx        # 生成元x坐标
        self.Gy = Gy        # 生成元y坐标
        self.name = name    # 曲线名称


# SM2国密算法推荐曲线参数（sm2p256v1）
sm2p256v1 = CurveFp(
    name="sm2p256v1",
    A=0xFFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFC,
    B=0x28E9FA9E9D9F5E344D5A9E4BCF6509A7F39789F515AB8F92DDBCBD414D940E93,
    P=0xFFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFF,
    N=0xFFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123,
    Gx=0x32C4AE2C1F1981195F9904466A39C9948FE30BBFF2660BE1715A4589334C74C7,
    Gy=0xBC3736A2F4F6779C59BDCEE36B692153D0A9877CC62A474002DF32E52139F0A0
)


# 椭圆曲线点乘法：计算 n*a (mod N)，使用Jacobian坐标加速计算
def multiply(a, n, N, A, P):
    return fromJacobian(jacobianMultiply(toJacobian(a), n, N, A, P), P)


# 椭圆曲线点加法：计算 a + b (mod P)
def add(a, b, A, P):
    return fromJacobian(jacobianAdd(toJacobian(a), toJacobian(b), A, P), P)


# 模逆运算：计算 a 在模 n 下的逆元，使用扩展欧几里得算法
def inv(a, n):
    if a == 0:
        return 0
    lm, hm = 1, 0          # 低阶系数和高阶系数
    low, high = a % n, n   # 余数和模数
    while low > 1:
        r = high // low    # 商
        nm, new = hm - lm * r, high - low * r  # 更新系数和余数
        lm, low, hm, high = nm, new, lm, low
    return lm % n


# 转换点坐标到Jacobian projective坐标系 (X, Y) -> (X, Y, 1)
def toJacobian(Xp_Yp):
    Xp, Yp = Xp_Yp
    return (Xp, Yp, 1)


# 从Jacobian坐标系转换回仿射坐标系 (X, Y, Z) -> (X/Z², Y/Z³) mod P
def fromJacobian(Xp_Yp_Zp, P):
    Xp, Yp, Zp = Xp_Yp_Zp
    z = inv(Zp, P)         # 计算Z的模逆
    return ((Xp * z ** 2) % P, (Yp * z ** 3) % P)


# Jacobian坐标系下的点加倍：计算 2*(Xp, Yp, Zp)
def jacobianDouble(Xp_Yp_Zp, A, P):
    Xp, Yp, Zp = Xp_Yp_Zp
    if not Yp:             # 无穷远点加倍仍为无穷远点
        return (0, 0, 0)
    ysq = (Yp ** 2) % P    # Y² mod P
    S = (4 * Xp * ysq) % P # 4XY² mod P
    M = (3 * Xp ** 2 + A * Zp ** 4) % P  # 3X² + AZ⁴ mod P
    nx = (M ** 2 - 2 * S) % P            # X' = M² - 2S mod P
    ny = (M * (S - nx) - 8 * ysq ** 2) % P  # Y' = M(S - X') - 8Y⁴ mod P
    nz = (2 * Yp * Zp) % P               # Z' = 2YZ mod P
    return (nx, ny, nz)


# Jacobian坐标系下的点加法：计算 (Xp,Yp,Zp) + (Xq,Yq,Zq)
def jacobianAdd(Xp_Yp_Zp, Xq_Yq_Zq, A, P):
    Xp, Yp, Zp = Xp_Yp_Zp
    Xq, Yq, Zq = Xq_Yq_Zq
    if not Yp:             # 若P为无穷远点，返回Q
        return (Xq, Yq, Zq)
    if not Yq:             # 若Q为无穷远点，返回P
        return (Xp, Yp, Zp)
    # 计算U1 = Xp*Zq², U2 = Xq*Zp², S1 = Yp*Zq³, S2 = Yq*Zp³
    U1 = (Xp * Zq ** 2) % P
    U2 = (Xq * Zp ** 2) % P
    S1 = (Yp * Zq ** 3) % P
    S2 = (Yq * Zp ** 3) % P
    if U1 == U2:
        if S1 != S2:       # 相同X不同Y，返回无穷远点
            return (0, 0, 1)
        return jacobianDouble((Xp, Yp, Zp), A, P)  # 点加倍
    H = U2 - U1            # H = U2 - U1
    R = S2 - S1            # R = S2 - S1
    H2 = (H * H) % P       # H² mod P
    H3 = (H * H2) % P      # H³ mod P
    U1H2 = (U1 * H2) % P   # U1*H² mod P
    nx = (R ** 2 - H3 - 2 * U1H2) % P  # X' = R² - H³ - 2U1H² mod P
    ny = (R * (U1H2 - nx) - S1 * H3) % P  # Y' = R(U1H² - X') - S1H³ mod P
    nz = (H * Zp * Zq) % P # Z' = H*Zp*Zq mod P
    return (nx, ny, nz)


# Jacobian坐标系下的点乘法：使用二进制扩展法计算 n*(Xp,Yp,Zp)
def jacobianMultiply(Xp_Yp_Zp, n, N, A, P):
    Xp, Yp, Zp = Xp_Yp_Zp
    if Yp == 0 or n == 0:  # 无穷远点或乘数为0，返回无穷远点
        return (0, 0, 1)
    if n == 1:             # 乘数为1，返回自身
        return (Xp, Yp, Zp)
    if n < 0 or n >= N:    # 确保n在[0,N)范围内
        return jacobianMultiply((Xp, Yp, Zp), n % N, N, A, P)
    if (n % 2) == 0:       # 偶数：n = 2k，计算2*k*P
        return jacobianDouble(jacobianMultiply((Xp, Yp, Zp), n // 2, N, A, P), A, P)
    # 奇数：n = 2k+1，计算2*k*P + P
    return jacobianAdd(jacobianDouble(jacobianMultiply((Xp, Yp, Zp), n // 2, N, A, P), A, P), (Xp, Yp, Zp), A, P)


# 私钥生成类：生成SM2私钥并计算对应的公钥
class CreatePrivateKey:
    def __init__(self, curve=sm2p256v1, secret=None):
        self.curve = curve                  # 椭圆曲线参数
        # 生成私钥（1 < secret < curve.N），若未提供则自动生成
        self.secret = secret or SystemRandom().randrange(1, curve.N)

    def publicKey(self):
        # 计算公钥：public_key = secret * G (G为曲线生成元)
        xPublicKey, yPublicKey = multiply((self.curve.Gx, self.curve.Gy), self.secret, A=self.curve.A, P=self.curve.P, N=self.curve.N)
        return CreatePublicKey(xPublicKey, yPublicKey, self.curve)

    def toString(self):
        # 私钥转为64位十六进制字符串
        return "{}".format(str(hex(self.secret))[2:].zfill(64))


class CreatePublicKey:
    def __init__(self, x, y, curve):
        self.x = x
        self.y = y
        self.curve = curve

    def toString(self, compressed=True):
        return {
            True: str(hex(self.x))[2:],
            False: "{}{}".format(str(hex(self.x))[2:].zfill(64), str(hex(self.y))[2:].zfill(64))
        }.get(compressed)


def create_key():
    priKey = CreatePrivateKey()
    pubKey = priKey.publicKey()
    return priKey.toString(), "04"+pubKey.toString(compressed=False)


if __name__ == "__main__":
    priKey = CreatePrivateKey()
    pubKey = priKey.publicKey()
    print(priKey.toString())
    print("04"+pubKey.toString(compressed=False))
    print("0451be14618fd214272bf7e235044b4b2474bfd5b9544f93a3fc0b3669153ea812cbffa08e56a5b1553de0bb65c4dba0fe232fac8d5023c5ce2d96e0fae2584cf7")

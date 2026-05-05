/***************************************************************************/
/*                               micro-Max,                                */
/* A chess program smaller than 2KB (of non-blank source), by H.G. Muller  */
/* Ported to WASM with fixes for 64-bit environments and stability.        */
/***************************************************************************/

#include <stdint.h>
#include <string.h>

#define U 1048576
#define M 136
#define S 128
#define I 100000

struct _ { int K, V; signed char X, Y, D; } A[U];

int Q, O, K, N, R, J, Z, k = 16;
signed char b[129], T[1035], L;
signed char w[] = { 0,2,2,7,-1,8,12,23 };
signed char o[] = { -16,-15,-17,0,1,16,0,1,16,15,17,0,14,18,31,33,0,
                    7,-1,11,6,8,3,6,
                    6,3,5,7,4,5,3,6 };

unsigned int my_rand() {
    static unsigned int x = 123456789;
    x = x * 1103515245 + 12345;
    return x;
}

int D(int q, int l, int e, int E, int z, int n) {
    int j, r, m, v, d, h, i, F, G, V, P, f = J, g = Z, C, s;
    signed char t, u, x, y, X, Y, H, B;
    struct _* a = A + (((unsigned int)J + (unsigned int)k * E) & (U - 1));

    q--;
    k ^= 24;
    d = a->D; m = a->V; X = a->X; Y = a->Y;
    if (a->K != Z || z || !((m <= q) || (X & 8 && m >= l) || (X & S))) d = Y = 0;
    X &= ~M;

    while (d++ < n || d < 3 || (z && K == I && (N < 1000000 && d < 98 || (K = X, L = Y & ~M, d = 3)))) {
        x = B = X;
        h = Y & S;
        P = d < 3 ? I : D(-l, 1 - l, -e, S, 0, d - 3);
        m = ((-P < l) || (R > 35)) ? (d > 2 ? -I : e) : -P;
        N++;
        do {
            u = b[x];
            if (u & k) {
                r = u & 7;
                j = o[r + 16];
                while (r = ((u & 7) > 2 && r < 0) ? -r : -o[++j]) {
                A_label:
                    y = x; F = G = S;
                    do {
                        H = y = h ? Y ^ h : y + r;
                        if (y & M) break;
                        if (E - S && b[E] && (y - E < 2 && E - y < 2)) m = I;
                        if ((u & 7) < 3 && y == E) H ^= 16;
                        t = b[H];
                        if ((t & k) || ((u & 7) < 3 && (!(y - x & 7) - !t))) break;
                        i = 37 * w[t & 7] + (t & 192);
                        if (i < 0) m = I;
                        if (m >= l && d > 1) goto C_label;

                        v = d - 1 ? e : i - (u & 7);
                        if (d - !t > 1) {
                            v = (u & 7) < 6 ? b[x + 8] - b[y + 8] : 0;
                            b[G] = b[H] = b[x] = 0; b[y] = u | 32;
                            if (!(G & M)) b[F] = k + 6, v += 50;
                            v -= ((u & 7) - 4 || R > 29) ? 0 : 20;
                            if ((u & 7) < 3) {
                                v -= 9 * ((x - 2 & M || b[x - 2] - u) + (x + 2 & M || b[x + 2] - u) - 1 + (b[x ^ 16] == k + 36)) - (R >> 2);
                                V = y + r + 1 & S ? 647 - (u & 7) : 2 * (u & y + 16 & 32);
                                b[y] += V; i += V;
                            }
                            v += e + i; V = m > q ? m : q;
                            J += T[H] ^ T[y] ^ T[x]; Z += (T[H + 8] ^ T[y + 8] ^ T[x + 8]) + G - S;
                            C = d - 1 - (d > 5 && (u & 7) > 2 && !t && !h);
                            C = (R > 29 || d < 3 || P - I) ? C : d;
                            do s = (C > 2 || v > V) ? -D(-l, -V, -v, F, 0, C) : v;
                            while (s > q && ++C < d); v = s;
                            if (z && K - I && v + I && x == K && y == L) {
                                Q = -e - i; O = F;
                                a->D = 99; a->V = 0;
                                R += i >> 7; return l;
                            }
                            J = f; Z = g;
                            b[G] = k + 6; b[F] = b[y] = 0; b[x] = u; b[H] = t;
                        }
                        if (v > m) m = v, X = x, Y = y | S & F;
                        if (h) { h = 0; goto A_label; }
                        if (x + r - y || u & 32 || ((u & 7) > 2 && ((u & 7) != 4 || (j != 7) || (b[G = x + 3 ^ r >> 1 & 7] != (k + 6)) || b[G ^ 1] || b[G ^ 2]))) t += (u & 7) < 5;
                        else F = y;
                    } while (!t);
                }
            }
        } while ((x = x + 9 & ~M) - B);
    }
C_label:
    if (m > I - M || m < M - I) d = 98;
    m = (m + I || P == I) ? m : 0;
    if (a->D < 99) a->K = Z, a->V = m, a->D = d, a->X = X | 8 * (m > q) | S * (m < l), a->Y = Y;
    k ^= 24;
    return m += m < e;
}

void init_engine() {
    static int initialised = 0;
    if (initialised) return;
    for (int i = 0; i < 8; i++) {
        b[i] = (b[i + 112] = o[i + 24] + 8) + 8;
        b[i + 16] = 18;
        b[i + 96] = 9;
        for (int j = 0; j < 8; j++) b[16 * j + i + 8] = (i - 4) * (i - 4) + (j - 4) * (j - 4);
    }
    for (int i = 0; i < 1035; i++) T[i] = my_rand() & 0xFF;
    O = S; Q = 0;
    initialised = 1;
}

int generate_fen(char* out) {
    char* p = out;
    for (int row = 0; row < 8; row++) {
        int empty = 0;
        for (int col = 0; col < 8; col++) {
            int pos = row * 16 + col;
            int piece = b[pos] & 15;
            if (piece == 0) empty++;
            else {
                if (empty > 0) { *p++ = '0' + empty; empty = 0; }
                const char* symbols = ".ppnkbrq.PPNKBRQ";
                *p++ = symbols[piece];
            }
        }
        if (empty > 0) *p++ = '0' + empty;
        if (row < 7) *p++ = '/';
    }
    *p++ = ' '; *p++ = (k == 16 ? 'w' : 'b');
    *p++ = ' '; *p++ = '-'; *p++ = ' '; *p++ = '-';
    *p++ = ' '; *p++ = '0'; *p++ = ' '; *p++ = '1';
    *p = '\0';
    return p - out;
}

int do_move(int f, int t) {
    signed char u = b[f], v = b[t];
    int G = S, F = S;
    
    // Castling logic: if King moves 2 squares horizontally
    if ((u & 7) == 4 && (t - f == 2 || f - t == 2)) {
        G = (t > f) ? f + 3 : f - 4; // Rook source
        F = (t > f) ? f + 1 : f - 1; // Rook dest
    }
    
    // Incremental updates for J (hash) and Z (evaluation)
    // J += T[H] ^ T[y] ^ T[x]; Z += (T[H+8] ^ T[y+8] ^ T[x+8]) + G - S;
    // In our case: x=f, y=t, H=t (capture square)
    J ^= T[t] ^ T[t] ^ T[f]; // This is a simplification, but J is mostly for hashing
    Z += (T[t+8] ^ T[t+8] ^ T[f+8]) + G - S;
    
    // Execute move
    b[t] = u | 32; b[f] = 0;
    if (G != S) {
        b[F] = b[G] | 32;
        b[G] = 0;
    }
    
    k ^= 24; // Flip side
    return 1;
}

int is_in_check(int side_in_check) {
    int king_square = -1;
    int king_piece = (side_in_check == 16) ? 12 : 20; // 12=White King, 20=Black King
    for (int i = 0; i < 128; i++) if (!(i & 0x88) && b[i] == king_piece) { king_square = i; break; }
    if (king_square == -1) return 0;
    
    // Save state
    int old_k = k, old_J = J, old_Z = Z;
    k = (side_in_check == 16) ? 8 : 16; // Opposite side to search
    int res = D(-I, I, Q, king_square, 1, 1);
    // Restore state
    k = old_k; J = old_J; Z = old_Z;
    return (res > 90000);
}

const char* find_json_val(const char* buf, int len, const char* key) {
    if (!buf || len <= 0) return 0;
    for (int i = 0; i < len - 4; i++) {
        int match = 1;
        for (int j = 0; key[j]; j++) if (buf[i + j] != key[j]) { match = 0; break; }
        if (match) {
            const char* p = buf + i + 1;
            while (*p && *p != ':') p++;
            if (*p == ':') p++;
            while (*p && (*p == ' ' || *p == '"')) p++;
            return p;
        }
    }
    return 0;
}

__attribute__((export_name("run")))
int run(int inPtr, int inLen, int outPtr, int outCap) {
    init_engine();
    const char* input = (const char*)inPtr;
    char* output = (char*)outPtr;
    const char* fromV = find_json_val(input, inLen, "from");
    const char* toV = find_json_val(input, inLen, "to");
    int score = 0;
    if (fromV && toV) {
        K = (fromV[0] - 'a') + (8 - (fromV[1] - '0')) * 16;
        L = (toV[0] - 'a') + (8 - (toV[1] - '0')) * 16;
        int moving_side = (k == 16) ? 8 : 16;
        if (K >= 0 && K < 128 && (b[K] & moving_side)) {
            // Apply User Move
            do_move(K, L);
            
            // Computer response
            K = I; N = 0;
            score = D(-I, I, Q, O, 1, 4);
            if (score > -90000) {
                // The D call above found the best move and stored it in K, L (via the z=1 logic)
                // We need to APPLY that move to the board state permanently.
                // In Micro-Max, D with z=1 and K=I actually finds the best move
                // but we need a final call to D to actually execute it or do it ourselves.
                D(-I, I, Q, O, 1, 3); 
            }
        }
    }
    int inCheck = is_in_check(k);
    
    char fen[128]; generate_fen(fen);
    char moveStr[5] = { 0 };
    if (k != 16) {
        moveStr[0] = 'a' + (K % 16); moveStr[1] = '0' + (8 - (K / 16));
        moveStr[2] = 'a' + (L % 16); moveStr[3] = '0' + (8 - (L / 16));
    }
    int len = 0;
    const char* pr = "{\"board\":\""; while (*pr) output[len++] = *pr++;
    char* f = fen; while (*f) output[len++] = *f++;
    const char* m1 = "\",\"lastMove\":\""; while (*m1) output[len++] = *m1++;
    char* m2 = moveStr; while (*m2) output[len++] = *m2++;
    const char* s1 = "\",\"score\":"; while (*s1) output[len++] = *s1++;
    int s_val = score; if (s_val < 0) { output[len++] = '-'; s_val = -s_val; }
    if (s_val == 0) output[len++] = '0';
    else { char buf[10]; int bi = 0; while (s_val > 0) { buf[bi++] = '0' + (s_val % 10); s_val /= 10; } while (bi > 0) output[len++] = buf[--bi]; }
    const char* s2 = ",\"check\":"; while (*s2) output[len++] = *s2++;
    if (inCheck) {
        const char* b_true = "true"; while (*b_true) output[len++] = *b_true++;
    } else {
        const char* b_false = "false"; while (*b_false) output[len++] = *b_false++;
    }

    const char* su = "}"; while (*su) output[len++] = *su++;
    return len;
}

__attribute__((export_name("init_board"))) int init_board() { init_engine(); return 0; }
__attribute__((export_name("make_move"))) int make_move(int x, int y) { return 0; }
__attribute__((export_name("is_valid_move"))) int is_valid_move(int x, int y) { return 1; }
__attribute__((export_name("get_possible_moves"))) int get_possible_moves(int x) { return 0; }

	.section .data
	@ 大域変数の定義
_Pi_var_x:
	.word 1
_Pi_var_y:
	.word 10
_Pi_var_z:
	.word -1
	.section .text
	.global _start
_start:
	@ 式をコンパイルした命令列

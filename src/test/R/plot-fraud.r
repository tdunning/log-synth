scores = read.delim("scores.tsv")
counts = read.delim("counts.tsv")
growth = read.delim("growth.tsv")

pdf("scores.pdf", width=4, height=3, pointsize=10)
old = par(mar=c(5.1, 4.5,3.1,2.1))
plot(score ~ merchant, data=scores, cex=0.5, col=rgb(0,0,0,alpha=0.7), pch=21,
     main="LLR score for different merchants", ylab=expression(-2*log(lambda)),
     yaxt='n')
axis(side=2, at=c(-10,0,10,20,30,40))
text(800, 30, "Compromised ATM", adj=0, cex=0.7)
arrows(750, 32, 120, 40, length=0.06, angle=15)
par(old)
dev.off()

pdf("counts.pdf", width=4, height=3, pointsize=10)
old = par(mar=c(4.3, 4.2,1.5,1.5))
plot(compromises ~ day, data=counts, col=rgb(0,0,0,alpha=0.7), pch=21, type='l', xlim=c(0,100), ylab="count")
lines(frauds ~ day, data=counts, col='red')

# annotates the compromise window
compromiseY = 350
text(19,compromiseY+30, "Compromise period", adj=0, cex=0.9)
arrows(7,compromiseY, 12.5,compromiseY, length=0.06, angle=15)
arrows(23,compromiseY, 17.5,compromiseY, length=0.06, angle=15)
lines(c(12.6,12.6),c(compromiseY-20,compromiseY+20))
lines(c(17.4,17.4),c(compromiseY-20,compromiseY+20))

# annotates the exploit windwo
exploitY = 230
text(34,exploitY+30, "Exploit period", adj=0.5, cex=0.9)

arrows(24,exploitY,19,exploitY,length=0.06, angle=15)
arrows(44,exploitY,49,exploitY,length=0.06, angle=15)
lines(c(18.9,18.9),c(exploitY-20,exploitY+20))
lines(c(49.1,49.1),c(exploitY-20,exploitY+20))

# indicates which line is which
legend(x=65, y=560, legend=c("compromises", "frauds"), fill=c('black', 'red'), cex=0.8)
par(old)
dev.off()

pdf("growth.pdf", width=4, height=3, pointsize=10)
old = par(mar=c(4.3, 4.2,3.5,1.5))
plot(score ~ day, data=growth, col=rgb(0,0,0,alpha=0.7), pch=21, type='l',
     ylim=c(0,45), ylab=expression(-2*log(lambda)), xlab="Day in exploit window",
     main="Growth of LLR during exploit window")

par(old)
dev.off()
